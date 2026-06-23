# AE2 Wireless Terminals: Pick-Block Backport

AI-generated slop; if you hate that, don't use it; I wouldn't.

----

A small client-and-server NeoForge mod for Minecraft **1.20.4** that backports AE2WTLib's
*"pick block through wireless terminal"* feature to the frozen 1.20.4 line.

With a wireless terminal carried in your inventory — linked to an ME network and in range — pressing
**pick block** (middle-click by default) on a block you have stored pulls it straight from the network
into your hand, the same way vanilla pick-block grabs from your inventory. Whatever was in the target
hotbar slot is pushed back into the network to make room.

The feature exists upstream in [AE2WTLib](https://github.com/Mari023/AE2WirelessTerminalLibrary) (Mari023's
"AE2 Wireless Terminals"), but only landed after the 1.20.4 line had already frozen, so the 17.x release
your 1.20.4 pack ships doesn't have it. This re-adds just that one behaviour.

## Requires

- Minecraft 1.20.4 **only** / NeoForge 20.4.x
- Applied Energistics 2 (17.x)
- AE2WTLib / "AE2 Wireless Terminals" (17.x)

Client-side install is enough for single-player and LAN; a dedicated server needs it installed too
(the extraction runs server-side).

## Why only 1.20.4?

Deliberately single-version. AE2WTLib added pick-block *after* the 1.20.4 line froze; the versions that
have it upstream — 1.20.1 (Forge/NeoForge) and 1.21+ — aren't targeted here, and 1.20.5+ switched items to
data components so this code wouldn't apply there anyway. 1.20.4 is the one still-used line left without
the feature, so it's the only version this targets — and the mod refuses to load on anything else.

## Credits & license

MIT. The pick-block logic is adapted from AE2WTLib by Mari023 and contributors (also MIT); AE2's storage
API is used under its LGPL-3.0 terms (linked, not copied). See `LICENSE`.
