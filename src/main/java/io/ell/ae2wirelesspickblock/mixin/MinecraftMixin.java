package io.ell.ae2wirelesspickblock.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.LocalCapture;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import io.ell.ae2wirelesspickblock.Ae2WirelessPickBlockConfig;
import io.ell.ae2wirelesspickblock.network.PickBlockPacket;

/**
 * Detects a "failed" pick-block on the client — middle-click on something the player doesn't already have
 * in their inventory — and asks the server to satisfy it from a carried wireless terminal's network.
 * Injection technique adapted from AE2WTLib PR #354 (pedroksl); MIT, see LICENSE.
 *
 * <p>Observational only: it never cancels vanilla pick-block, it just fires an extra request when vanilla
 * came up empty, so it coexists with other middle-click hooks (Mouse Tweaks, inventory sorters, …).
 *
 * <p>The injection point and captured locals are pinned to the decompiled 1.20.4 {@code
 * Minecraft#pickBlock} (this mod targets only 1.20.4, §2): we inject right after the {@code
 * Inventory.findSlotMatchingItem} call and read its result ({@code slot}) plus the picked {@code stack}.
 * {@code CAPTURE_FAILHARD} verifies the full local list at apply-time, so a mismatch fails loudly at load
 * rather than misbehaving silently — preferable to ordinal-based capture here, since the mixin can't be
 * runtime-verified from the build alone.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

   @Shadow
   public LocalPlayer player;

   @Inject(method = "pickBlock", at = @At(value = "INVOKE_ASSIGN",
         target = "Lnet/minecraft/world/entity/player/Inventory;findSlotMatchingItem(Lnet/minecraft/world/item/ItemStack;)I"),
         locals = LocalCapture.CAPTURE_FAILHARD)
   private void ae2wpb$onFailedPickBlock(CallbackInfo ci,
         boolean creative,
         BlockEntity blockEntity,
         HitResult.Type hitResultType,
         ItemStack stack,
         Inventory inventory,
         int slot) {
      if (slot != -1 || creative || player.isSpectator()) {
         return; // already in the player's inventory, or creative/spectator (vanilla/AE2 handle those)
      }
      if (Ae2WirelessPickBlockConfig.pickBlockFromNetwork()) {
         ae2wpb$requestNetworkPick(stack);
      }
   }

   @Unique
   private void ae2wpb$requestNetworkPick(ItemStack stack) {
      var connection = Minecraft.getInstance().getConnection();
      // Fail soft on servers without this mod: only send if our (optional) channel was negotiated.
      if (connection != null && NetworkRegistry.getInstance().isConnected(connection, PickBlockPacket.ID)) {
         PacketDistributor.SERVER.noArg().send(new PickBlockPacket(stack));
      }
   }
}
