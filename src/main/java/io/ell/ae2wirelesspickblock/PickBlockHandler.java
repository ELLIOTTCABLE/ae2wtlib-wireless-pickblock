/*
 * The network extraction below is adapted from AE2WirelessTerminalLibrary (AE2WTLib) by Mari023 and
 * contributors — specifically the 1.20.1 pick-block backport in PR #354 (pedroksl),
 * de.mari_023.ae2wtlib.AE2wtlibEvents#pickBlock. AE2WTLib is MIT-licensed and so is this file (see
 * LICENSE). It only *links* AE2's LGPL storage API; no AE2 source is copied here.
 */
package io.ell.ae2wirelesspickblock;

import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.stacks.AEItemKey;
import appeng.api.storage.MEStorage;
import appeng.me.helpers.PlayerSource;

import de.mari_023.ae2wtlib.wct.CraftingTerminalHandler;

/**
 * Server-side extraction: satisfy a failed pick-block by pulling the requested item from the player's
 * linked ME network, stowing whatever sat in the chosen hotbar slot back into the network to make room.
 *
 * <p>Authoritative and self-contained — everything it acts on is re-derived from the player's own carried
 * terminal, so {@code requested} is only ever a lookup key. Every failure (no terminal, out of range, null
 * grid/storage, item not stored, displaced item won't fit) is an ordinary no-op early return: it never
 * throws and never conjures items.
 */
public final class PickBlockHandler {

   private PickBlockHandler() {
   }

   public static void pickBlock(ServerPlayer player, ItemStack requested) {
      AEItemKey requestedKey = AEItemKey.of(requested);
      if (requestedKey == null) {
         return; // empty, or not representable as a network key
      }

      var terminal = CraftingTerminalHandler.getCraftingTerminalHandler(player);
      if (terminal.getCraftingTerminal().isEmpty() || !terminal.inRange()) {
         return; // no carried terminal, or it's unlinked / out of range / unpowered
      }
      IGrid grid = terminal.getTargetGrid();
      if (grid == null || grid.getStorageService() == null) {
         return;
      }
      MEStorage network = grid.getStorageService().getInventory();
      var source = new PlayerSource(player, null);

      Inventory inventory = player.getInventory();
      int slot = inventory.getSuitableHotbarSlot();
      ItemStack displaced = inventory.getItem(slot);
      AEItemKey displacedKey = displaced.isEmpty() ? null : AEItemKey.of(displaced);
      if (!displaced.isEmpty() && displacedKey == null) {
         return; // can't represent the to-be-stowed item; leave the slot untouched
      }

      // Simulate both legs before committing either: act only if the displaced stack fully fits back into
      // the network and the requested item is actually stored.
      if (displacedKey != null
            && network.insert(displacedKey, displaced.getCount(), Actionable.SIMULATE, source) < displaced.getCount()) {
         return;
      }
      int amount = Math.max(1, requested.getMaxStackSize() / 2);
      if (network.extract(requestedKey, amount, Actionable.SIMULATE, source) == 0) {
         return; // not stored (v1 doesn't craft-if-missing — see IMPLEMENTATION_PLAN §4)
      }

      // Commit: stow the displaced stack so the slot is free, then pull the requested item into it.
      if (displacedKey != null) {
         long stowed = network.insert(displacedKey, displaced.getCount(), Actionable.MODULATE, source);
         if (stowed < displaced.getCount()) {
            displaced.setCount(displaced.getCount() - (int) stowed); // network filled between sim and commit
            inventory.setItem(slot, displaced);
            return;
         }
      }
      long extracted = network.extract(requestedKey, amount, Actionable.MODULATE, source);
      if (extracted == 0) {
         inventory.setItem(slot, ItemStack.EMPTY); // displaced already stowed; nothing came back
         return;
      }
      inventory.setItem(slot, requestedKey.toStack((int) extracted));
      inventory.selected = slot;
      player.connection.send(new ClientboundSetCarriedItemPacket(slot));
   }
}
