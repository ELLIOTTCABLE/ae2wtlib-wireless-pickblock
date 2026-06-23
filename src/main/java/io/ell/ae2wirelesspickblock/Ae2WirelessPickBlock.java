package io.ell.ae2wirelesspickblock;

import com.mojang.logging.LogUtils;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModLoadingContext;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlerEvent;

import org.slf4j.Logger;

import io.ell.ae2wirelesspickblock.network.PickBlockPacket;

/**
 * Entry point. Wires up the C2S payload (both sides) and the client config toggle. The behaviour itself
 * lives in the client mixin ({@code mixin.MinecraftMixin}) and the server handler ({@link PickBlockHandler}).
 */
@Mod(Ae2WirelessPickBlock.MODID)
public final class Ae2WirelessPickBlock {
   public static final String MODID = "ae2wirelesspickblock";
   public static final Logger LOGGER = LogUtils.getLogger();

   public Ae2WirelessPickBlock(IEventBus modBus, ModContainer container) {
      modBus.addListener(this::registerPayloads);
      if (FMLEnvironment.dist == Dist.CLIENT) {
         ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, Ae2WirelessPickBlockConfig.SPEC);
      }
      LOGGER.info("[{}] loaded", MODID);
   }

   // optional(): a client carrying this mod can still join a server without it (the client send is
   // additionally guarded). serverbound only: the client asks, the server extracts.
   private void registerPayloads(RegisterPayloadHandlerEvent event) {
      event.registrar(MODID).optional().play(
            PickBlockPacket.ID, PickBlockPacket::decode,
            builder -> builder.server(PickBlockPacket::handle));
   }
}
