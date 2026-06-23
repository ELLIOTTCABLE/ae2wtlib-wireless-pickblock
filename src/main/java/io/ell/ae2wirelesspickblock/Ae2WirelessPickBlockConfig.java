package io.ell.ae2wirelesspickblock;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client config gating whether a failed middle-click asks the server for a network pick-block.
 *
 * <p>Upstream AE2WTLib gates this per-terminal (an NBT flag on 1.20.1, a data component on 1.21); both
 * postdate the frozen 17.x line, so we can't reuse them. A single client toggle suffices because the
 * server-side extraction is authoritative regardless — it only ever moves an item the player could
 * already pull from the terminal GUI — so this just controls whether the client bothers to ask.
 */
public final class Ae2WirelessPickBlockConfig {
   public static final ModConfigSpec SPEC;
   private static final ModConfigSpec.BooleanValue PICK_BLOCK_FROM_NETWORK;

   static {
      var builder = new ModConfigSpec.Builder();
      PICK_BLOCK_FROM_NETWORK = builder
            .comment(
                  "Middle-click a block you don't have in your inventory, while carrying a linked wireless",
                  "terminal in range, to pull it from your ME network into your hand (displacing whatever",
                  "was in that hotbar slot back into the network). Turn off to restore vanilla pick-block.")
            .define("pickBlockFromNetwork", true);
      SPEC = builder.build();
   }

   private Ae2WirelessPickBlockConfig() {
   }

   public static boolean pickBlockFromNetwork() {
      return PICK_BLOCK_FROM_NETWORK.getAsBoolean();
   }
}
