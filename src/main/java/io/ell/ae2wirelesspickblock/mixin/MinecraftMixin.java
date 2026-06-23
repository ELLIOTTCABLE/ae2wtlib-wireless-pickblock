package io.ell.ae2wirelesspickblock.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

import io.ell.ae2wirelesspickblock.Ae2WirelessPickBlockConfig;
import io.ell.ae2wirelesspickblock.network.PickBlockPacket;

/**
 * Asks the server to satisfy a failed pick-block from a carried wireless terminal's network. Observational
 * — it returns vanilla's slot result untouched — so it coexists with other middle-click hooks (Mouse
 * Tweaks, inventory sorters, …). Technique adapted from AE2WTLib PR #354 (pedroksl); MIT, see LICENSE.
 *
 * <p>Anchored on the {@code findSlotMatchingItem} call inside {@code Minecraft#pickBlock}, not on captured
 * locals: the call's result is the "already in inventory" slot (-1 = none) and the picked stack is its sole
 * {@link ItemStack} argument — so we sidestep ordinal/name-based local capture, the one fragile part on
 * 1.20.4.
 */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

   @Shadow
   public LocalPlayer player;

   @ModifyExpressionValue(method = "pickBlock", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/world/entity/player/Inventory;findSlotMatchingItem(Lnet/minecraft/world/item/ItemStack;)I"))
   private int ae2wpb$pickFromNetworkOnMiss(int slot, @Local ItemStack picked) {
      if (slot == -1 && !player.getAbilities().instabuild && !player.isSpectator()
            && Ae2WirelessPickBlockConfig.pickBlockFromNetwork()) {
         ae2wpb$requestNetworkPick(picked);
      }
      return slot;
   }

   @Unique
   private void ae2wpb$requestNetworkPick(ItemStack stack) {
      var connection = Minecraft.getInstance().getConnection();
      // fail soft on servers without this mod: only send if our optional channel was negotiated
      if (connection != null && NetworkRegistry.getInstance().isConnected(connection, PickBlockPacket.ID)) {
         PacketDistributor.SERVER.noArg().send(new PickBlockPacket(stack));
      }
   }
}
