package io.ell.ae2wirelesspickblock;

import com.mojang.logging.LogUtils;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import org.slf4j.Logger;

/**
 * Entry point — scaffold stub. No feature behaviour yet; see IMPLEMENTATION_PLAN.md.
 *
 * <p>When implementing, this is where you'd register the client config and the C2S payload
 * (NeoForge {@code RegisterPayloadHandlerEvent}) off {@code modBus}. The actual behaviour lives in a
 * client mixin into {@code Minecraft.pickBlock} plus the server-side extraction handler.
 */
@Mod(Ae2WirelessPickBlock.MODID)
public final class Ae2WirelessPickBlock {
   public static final String MODID = "ae2wirelesspickblock";
   public static final Logger LOGGER = LogUtils.getLogger();

   public Ae2WirelessPickBlock(IEventBus modBus, ModContainer container) {
      LOGGER.info("[{}] loaded", MODID);
   }
}
