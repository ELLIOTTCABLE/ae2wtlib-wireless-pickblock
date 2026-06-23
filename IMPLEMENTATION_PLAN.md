# AE2 Wireless Terminals: Pick-Block Backport — implementation plan

For a fresh implementation context ("conductor"). This is a guide, not a contract: the recommendations
below are where the research landed, but you're as capable as the author — if you find a better path,
take it. The scaffold around this file is a NeoForge 1.20.4 MDK (copied from the sibling EMI backport and
re-identified); no behavioural code exists yet.

## 1. Goal

With a wireless terminal carried in the player's inventory — linked to an ME network and in range —
pressing **pick block** (middle-click) on a block that's stored in the network pulls a stack of it into
the player's hand, displacing whatever was in that hotbar slot back into the network. This is vanilla
pick-block's "grab from inventory" extended to "grab from your ME network".

It's a backport: the feature exists upstream in AE2WTLib (Mari023's "AE2 Wireless Terminals"), but landed
after the 1.20.4 line had frozen, so the 17.x AE2WTLib that 1.20.4 packs ship doesn't have it. We re-add
just that behaviour as a separate, publishable mod. (AE2 core will not get it — the AE2 maintainer
redirects this to AE2WTLib.)

## 2. Target environment

| | |
|---|---|
| Minecraft | 1.20.4 |
| NeoForge | 20.4.248 (pinned in `gradle.properties`; matches the FTB NeoTech pack) |
| AE2 | 17.13.0-beta (last 1.20.4 release) |
| AE2WTLib | 17.12.0-beta (last 1.20.4 release; ships in the pack, lacks the feature) |

Scope is deliberately narrow to ~1.20.4. We don't target versions that already include the feature, so
there's no need for multi-version mixin gymnastics. (The name avoids a version number in case a future
MC version that's *also* missing the feature is worth supporting, but that's hypothetical.)

## 3. The mechanism, and exactly where to read it

The whole feature is: detect a "failed" pick-block on the client, ask the server to satisfy it from the
network. AE2WTLib implements this two different ways depending on MC version, and **the 1.20.1 one is our
template** (1.20.4 has no server-side pick-item method to hook, just like 1.20.1; and 1.20.4 predates data
components, just like 1.20.1). All references are on disk:

**Clone:** `~/Sync/Code/Source/AE2WirelessTerminalLibrary`

- **Template — `forge/1.20.1` branch** (commit `95a3b220`, PR #354 "Backport Pick Block and Stow"):
  - `…/mixin/MinecraftMixin.java` — client hook: `@Inject` into `Minecraft#pickBlock` at `INVOKE_ASSIGN`
    after `Inventory.findSlotMatchingItem`, reads the "already-have-it slot" local; if it's `-1` and the
    player isn't creative/spectator, fire the packet.
  - `…/networking/c2s/PickBlockPacket.java` — the C2S packet (Forge SimpleChannel style; you'll reimplement
    the transport on NeoForge, keep the shape).
  - `…/AE2wtlibEvents.java` → `pickBlock(ServerPlayer, ItemStack)` — the server extraction. Uses the
    1.20.x-correct calls: `inventory.selected = slot`, `player.connection.send(new
    ClientboundSetCarriedItemPacket(...))`, `new PlayerSource(player, null)`. Read this one closely; it's
    the most faithful to what you'll write.
  - View the whole diff as a crib sheet: `git -C <clone> show 95a3b220`.

- **Modern reference — `main` branch** (richer, but 1.21 idioms — don't copy verbatim):
  - `…/AE2wtlibEvents.java` → `pickBlock` — clearer simulate-then-commit ordering and the craft-if-missing
    branch; uses `getSuitableHotbarSlot`/`setSelectedSlot` (1.21 names).
  - `…/mixin/ServerGamePacketListenerImplMixin.java` — the 1.21 server-mixin approach (NOT usable on
    1.20.4; shown only so you recognise why we use the client approach instead).

- **The infrastructure we ride on — `17.12.0-beta` tag** (present in the pack):
  - `…/wct/CraftingTerminalHandler.java` — `getCraftingTerminalHandler(player)` (static) →
    `getTargetGrid()` / `inRange()` / `getCraftingTerminal()` / `getLocator()`. This is the hard part of
    "find the carried terminal, its linked grid, and whether it's in range/powered", already solved and
    frozen. `git -C <clone> show 17.12.0-beta:src/main/java/de/mari_023/ae2wtlib/wct/CraftingTerminalHandler.java`

**AE2 clone:** `~/Sync/Code/Source/Applied-Energistics-2`, tag `neoforge/v17.13.0-beta` — for the storage
API surface (`Actionable`, `AEItemKey`, `PlayerSource`, `IGrid#getStorageService().getInventory()`,
`CraftAmountMenu.open`).

**Sibling project:** `~/Sync/Code/ae2-emi-crafting-backport` — a working NeoForge 20.4 mixin mod by the
same author; useful as a style/scaffolding reference (config pattern, attribution headers, mods.toml).
Note its license is LGPL because it copied AE2 *source*; ours is MIT (§8) — don't copy its license.

## 4. Shape of the implementation

Roughly three pieces plus a toggle. Suggested package layout is already stubbed under
`io.ell.backports.ae2wtlib_wireless_pickblock` (`mixin/`, `network/`).

1. **Client mixin** `mixin/MinecraftMixin` into `net.minecraft.client.Minecraft#pickBlock()`.
   - On 1.20.4 the method is still named `pickBlock` (renamed only in 1.21.2+), so this is the right hook.
   - The 1.20.1 reference captures locals via `LocalCapture.CAPTURE_FAILHARD` with the full local list —
     brittle across versions. **Recommendation:** use MixinExtras `@Local` (bundled with NeoForge 20.4),
     by name if Parchment gives one, else by ordinal/type, pinned against the *decompiled 1.20.4*
     `Minecraft.pickBlock` (NeoGradle gives you sources). This local-capture is the one genuinely fragile
     part of the whole mod — worth getting exactly right and leaving a comment explaining the pin.
   - Keep it observational: don't cancel vanilla; just send the packet when the "already-have-it" slot is
     `-1` and the player isn't creative/spectator. Other mods also hook pick-block (Mouse Tweaks, Pick
     Block Pro, inventory sorters) — assume you're not the only injector.
   - Add the class to `ae2wtlib_wireless_pickblock.mixins.json` `"client"`.

2. **C2S packet** `network/PickBlockPacket` on NeoForge 20.4's payload system.
   - 20.4 uses `RegisterPayloadHandlerEvent` → `IPayloadRegistrar` (`.playToServer(...)`), and
     `CustomPacketPayload` with `id()` + `write(FriendlyByteBuf)`. ~SUSPECT those are the exact 20.4 shapes
     — this API changed noticeably between 1.20.4 and 1.21, so verify against 20.4 docs or a 20.4-era mod,
     not 1.21 examples. Register it from the `@Mod` constructor's `modBus`.
   - Payload carries the picked item's identity. Handle on the server thread (`ctx.enqueueWork`) and call
     the extraction with the sending `ServerPlayer`.

3. **Server extraction** (adapt the 1.20.1 `AE2wtlibEvents.pickBlock(ServerPlayer, ItemStack)`).
   - `CraftingTerminalHandler.getCraftingTerminalHandler(player)`; bail unless the terminal is present,
     `getTargetGrid()` and its `getStorageService()` are non-null, and (your call) `inRange()`.
   - `inventory.getSuitableHotbarSlot()`; simulate-insert the displaced item back into the network and
     simulate-extract the target (`Actionable.SIMULATE`). If the displaced item won't fit, or the target
     isn't actually stored, no-op. Then commit (`MODULATE`): insert displaced, extract target, `setItem`,
     `inventory.selected = slot`, and sync with `ClientboundSetCarriedItemPacket`.
   - `PlayerSource` ctor on AE2 17.x takes `(player, null)` (the 1.20.1 ref confirms; `main`'s 1-arg form
     is newer).
   - Stack size: 1.20.1 pulls `max(1, maxStackSize/2)`, `main` pulls a full stack. Minor; pick one.
   - **Optional (stretch): craft-if-missing.** If the target isn't stored but is craftable, the references
     open `CraftAmountMenu.open(player, cTHandler.getLocator(), what, 1)`. Feasible on 17.x (`getLocator()`
     exists at the tag; verify `CraftAmountMenu.open`'s signature). Reasonable to ship v1 without it.

**Toggle.** Upstream gates per-terminal: 1.21 via a `PICK_BLOCK` data component, 1.20.1 via an NBT tag
(`ItemWT.getBoolean(terminal, AE2wtlibTags.PICK_BLOCK)`). Both that tag constant and the component are
AE2WTLib's own and postdate 17.x, so you can't reuse them. Options, simplest first:
- **Client `ModConfigSpec` boolean that gates whether the client sends the packet.** Per-player, zero GUI
  work, and safe because the server-side extraction is authoritative regardless (§5). Recommended for v1.
- A per-terminal NBT flag you define yourself — faithful to upstream UX, but you'd need a way to set it
  (keybind or command; the 1.20.1 backport added a settings screen + packet, which is more than this mod
  needs).

Default on/off is the author's preference — the sibling EMI backport defaulted *on* against upstream's
*off*; mirror unless told otherwise.

## 5. Correctness & safety model (worth internalising)

- **No persistent/world state.** The mod only moves items between an existing network and an existing
  inventory at the moment of a click. Safe to add to, or remove from, a save at any time.
- **Client-sent item is untrusted but harmless.** The server extracts only what's actually in the
  player's linked network, gated by terminal presence + range/power + `SIMULATE`-before-`MODULATE`. The
  worst a forged packet achieves is pulling an item the player could already pull from the terminal GUI —
  no dupe, no escalation. Still, treat the incoming item as data, not a command.
- **No-op, never throw,** on: empty pick; no carried terminal; terminal unlinked / out of range /
  unpowered; null grid or storage service; item not stored. Each is an ordinary early return.
- **Skip creative/spectator** (vanilla/AE2 handle those; matches upstream).

## 6. Defensive notes (explicit goal of this mod)

- **Server may lack the mod.** With `displayTest=IGNORE_ALL_VERSION`, a client carrying this mod can join
  a server without it. Sending the payload must then fail soft — NeoForge won't have the channel
  registered on that connection. Guard the send (check the connection knows the channel, or wrap
  defensively) so vanilla/modless servers don't error. Single-player/LAN run the integrated server, so the
  client install suffices there; dedicated servers need it installed too.
- **AE2WTLib version tolerance.** The methods used exist at 17.12.0-beta; -GUESS they're stable across the
  17.x point releases your pack might use, but a quick check against the earliest is cheap.
- **We compile against AE2WTLib internals** (`CraftingTerminalHandler` is not a stability-guaranteed API).
  That's fine *because 17.x is frozen* — it can't shift under you — but note it so any future version bump
  is a deliberate re-check rather than a surprise.
- **Don't assume exclusivity** on the pick-block path; coexist with other middle-click consumers.

## 7. Build wiring

`build.gradle` already has the repos (ModMaven, Modrinth maven) and the AE2 + AE2WTLib `compileOnly`
lines **commented out**, so the bare stub builds green and you can verify the toolchain first. Then:

- Resolve AE2WTLib: try `de.mari_023:ae2wtlib:17.12.0-beta` from ModMaven; ~SUSPECT it's there but
  unverified. If it doesn't resolve, the reliable path (same trick the EMI backport documents for AE2) is
  the exact jar from the pack's `mods/` folder via `compileOnly files("libs/ae2wtlib-17.12.0-beta.jar")`.
- Uncomment both `compileOnly` deps (`transitive = false` on each — we only want their own classes).
- Mixin/refmap: NeoForge 20.4 is Mojmap and we mixin only vanilla `Minecraft` (+ reference deobf AE2/
  AE2WTLib types), so — like the sibling EMI mod — no refmap should be needed; confirm the build emits a
  working mixin.
- Dev-client runtime would need the full AE2 + AE2WTLib dependency tree (curios, etc.) on `localRuntime` —
  heavy. Testing in the actual pack instance is usually faster (see §9).

## 8. Licensing

MIT. The pick-block logic is adapted from AE2WTLib, which is **MIT** (© 2021 mari_023) — so unlike the
EMI backport (which copied LGPL AE2 *source*), we can be MIT. We only *link* AE2's LGPL API, which LGPL
permits without imposing copyleft. Keep an attribution header on the adapted server-logic file pointing at
AE2WirelessTerminalLibrary (Mari023 et al.; pedroksl authored the 1.20.1 injection technique in #354), and
the `LICENSE` already carries the dual copyright line.

## 9. Definition of done (suggested)

- `./gradlew build` is green — first with the bare stub (toolchain check), then with deps + mixin enabled.
- In the pack: carry a linked wireless terminal, toggle on, stand in range. Middle-click a block you have
  stored → it lands in hand; the displaced hotbar item returns to the network. Out of range / not stored /
  no terminal / toggle off → ordinary vanilla behaviour. Pull the item from storage → it stops working for
  that item.
- No crash joining a modless/vanilla server (send no-ops). SP works client-only; dedicated server needs
  the server install.
- Attribution header present (§8).

## 10. Things to verify (flagged, not blocking)

- The `Minecraft.pickBlock` local-capture against decompiled 1.20.4 sources (the fragile bit).
- The exact NeoForge 20.4 payload API shapes (§4.2) — verify against 20.4, not 1.21.
- AE2WTLib maven coordinate vs the `./libs` jar (§7).
- That the Wireless **Universal** Terminal (WUT) the pack uses is reached by `CraftingTerminalHandler` at
  17.x — `WUTHandler` exists at the tag, so likely; confirm in-game.
- `CraftAmountMenu.open`'s signature at AE2 17.13.0-beta, if you implement the craft-if-missing stretch.

## 11. Scope & the author's longer-term wishes

- **v1 is just pick-block.** Keep it tight and shippable.
- The broader interest is a small suite of "everyday behaviours with an equipped wireless terminal".
  AE2WTLib added pick-block, **restock** (auto-refill hotbar from the network) and **stow** as post-freeze
  toggles; restock/stow are plausible future siblings also missing from 1.20.4 (restock infra is partly
  present in 17.x — verify before counting it). If the mod grows that way, a broader name than
  "pick-block" would fit — noted so the name isn't a trap, not a request to rename now.
- **Graceful degradation** is an explicit stretch idea: sourcing pick-block from, say, a Sophisticated
  Storage backpack's contents when the player hasn't reached AE2 yet. Out of scope for v1 — but if it's
  cheap to make the extraction *source* an interface rather than hard-wiring the AE2 grid, that leaves the
  door open. Don't build it yet.

## 12. Naming

`mod_id` / package / folder are `ae2wtlib_wireless_pickblock` / `io.ell.backports.ae2wtlib_wireless_pickblock` /
`ae2wtlib-wireless-pickblock`. All a suggestion — trivially changeable if the author prefers something
else (e.g. a broader name if the suite in §11 materialises).
