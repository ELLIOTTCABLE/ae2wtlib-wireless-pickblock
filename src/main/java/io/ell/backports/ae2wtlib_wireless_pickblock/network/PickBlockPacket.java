package io.ell.backports.ae2wtlib_wireless_pickblock.network;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.handling.PlayPayloadContext;

import io.ell.backports.ae2wtlib_wireless_pickblock.Ae2wtlibWirelessPickblock;
import io.ell.backports.ae2wtlib_wireless_pickblock.PickBlockHandler;

/**
 * Client → server request to satisfy a failed pick-block from the player's linked ME network.
 *
 * <p>The carried {@link ItemStack} is the block the client tried to pick. It's untrusted — {@link
 * PickBlockHandler} re-derives everything it acts on from the player's own terminal and network and uses
 * this stack only as a lookup key — so a forged packet can at worst pull an item the sender could already
 * take from the terminal GUI.
 *
 * <p>NeoForge 20.4 payload shape (id + write + a static reader), modelled on AE2's own serverbound
 * packets. Registered {@code optional} in {@link Ae2wtlibWirelessPickblock} so a client carrying this mod can
 * still join a server without it.
 */
public record PickBlockPacket(ItemStack item) implements CustomPacketPayload {

   public static final ResourceLocation ID = new ResourceLocation(Ae2wtlibWirelessPickblock.MODID, "pick_block");

   public static PickBlockPacket decode(FriendlyByteBuf buf) {
      return new PickBlockPacket(buf.readItem());
   }

   @Override
   public void write(FriendlyByteBuf buf) {
      buf.writeItem(item);
   }

   @Override
   public ResourceLocation id() {
      return ID;
   }

   /** Server-side entry point: hop to the main thread, then extract from the network. */
   public void handle(PlayPayloadContext context) {
      context.workHandler().execute(() -> {
         if (context.player().orElse(null) instanceof ServerPlayer player) {
            PickBlockHandler.pickBlock(player, item);
         }
      });
   }
}
