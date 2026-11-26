# God Wand 1.7.0

One wand with ten skills: walk through mountains, pull enemies into a gravity orb, raise a drill, unleash Supernova, fire a stone projectile, hold **I am Atomic** until you choose to release, fire Fuga’s blazing arrow, open the **Silent Gate**, collide Blue and Red into **Purple**, or command up to 48 orbiting blocks in **Block Dominion** to fly on sculpted block wings, lock enemies in a Monolith Cage, and slam down in a Cataclysmic Meteor Dive.

[Wiki and guides](https://god-wand-wiki.vercel.app/) · [CurseForge downloads](https://www.curseforge.com/minecraft/mc-mods/god-wand) · [Full mod description](DESCRIPTION.md) · [Changelog](CHANGELOG.md)

| Minecraft | Loader | Java | Installable file |
| --- | --- | --- | --- |
| 1.21.1 | Fabric + Fabric API | 21 | `godwand-fabric-1.7.0.jar` |
| 1.21.1 | NeoForge | 21 | `godwand-neoforge-1.7.0.jar` |
| 1.20.1 | Fabric + Fabric API | 17 | `godwand-fabric-1.20.1-1.7.0.jar` |
| 1.20.1 | Forge | 17 | `godwand-forge-1.20.1-1.7.0.jar` |
| 1.21.11 | Fabric + Fabric API | 21 | `godwand-fabric-1.21.11-1.7.0.jar` |
| 1.21.11 | NeoForge | 21 | `godwand-neoforge-1.21.11-1.7.0.jar` |
| 26.1.2 | Fabric + Fabric API | 25 | `godwand-fabric-26.1.2-1.7.0.jar` |
| 26.1.2 | NeoForge | 25 | `godwand-neoforge-26.1.2-1.7.0.jar` |
| 26.3 | Fabric + Fabric API | 25 | `godwand-fabric-26.3-1.7.0.jar` |
| 26.3 | NeoForge | 25 | `godwand-neoforge-26.3-1.7.0.jar` |

Install one God Wand jar matching your exact Minecraft version and loader. The mod is required on the client and server. For multiplayer, update God Wand on both the server and every player’s client.

## Skills and controls

Hold **V** with the wand to select a skill (or bind **Next skill** / **Previous skill** in Controls). Press **K** for client graphics, HUD, controls guide, and model settings.

1. **Spatial Path:** toggle a temporary tunnel and shield. Tap Shift while active for a bridge made from nearby blocks.
2. **Singularity:** hold and release right-click to launch a gravity orb. Right-click again to collapse it early. Displaced terrain returns afterward.
3. **Earth Drill:** charge a drill of floating blocks and slam it into the ground.
4. **Supernova:** hold right-click for seven seconds to release a black hole and explosion.
5. **Stone Cannon:** charge and release a piercing stone projectile.
6. **I am Atomic:** hold through the buildup, ATOMIC pose and compression. The dark pause waits for you to release right-click, triggering the blast. Movement is locked and incoming damage is blocked during the cast. Esc/F5 skips its camera; skipping after detonation also restores movement.
7. **Fuga:** hold to form a fire arrow, aim while standing still, then release to fire. Releasing before the arrow is ready cancels. Toggle its camera in **K**.
8. **Silent Gate:** a giant magic circle opens the sky and a winged gate descends. Right-click to command it (or wait about seven seconds and it fires by itself): hundreds of circles rain beams on every monster around you and a golden beam finishes the strongest. Sneak while it forms to stop it. Toggle its camera in **K**.
9. **Purple:** hold left-click for **Blue** (gravity orb), hold right-click for **Red** (repulsive blast), fire Red into Blue to fuse them into **Purple**, or hold both buttons to launch a purple sphere straight ahead.
10. **Block Dominion:** **Shift + Left-click** to toggle telekinetic dominion over up to 48 nearby blocks. Left-click a block to lift it, left-click air for **Earthen Surge** (pulling up to 24 blocks from the ground), or left-click a mob to lock it in a **Monolith Cage** (8 blocks, impaled by 6 lances). Right-click to fire high-speed block shots or hold right-click for a rapid volley. **Double-tap Space** (with 6+ blocks) to form **Block Wings** with Hover/Glide (`Shift + Space`), Boost (`Ctrl` / `Space`), Orbital Volley (`Shift + RMB`), Seismic Hero Landing upheaval, and a 3-tier **Meteor Dive** when diving straight down. Orbiting blocks also passively parry incoming hits.

AUTO selects Aqua Heartia for skills 3/5, Shadow Blade for skill 6, Silent Witch Staff for skill 8, and Eclipse for the others. **K** also supports manual model choice and separate toggles for the skill HUD and Block Dominion controls guide.

## Terrain and modpacks

[Balance settings](docs/BALANCE_CONFIG.md) cover per-skill/per-charge damage and cooldowns. All wand appearances deal 5 normal attack damage.

Permanent terrain changes are configurable in `config/godwand-server.json`; defaults retain destructive spell behavior. Use the [terrain guide and presets](docs/TERRAIN_CONFIG.md) for damage-only spells or reversible block movement. When terrain damage is disabled (`terrainDamage: false` or `blockDominionTerrainDamage: false`), **Block Dominion** enters full Safe Mode: it conjures virtual blocks instead of mining world blocks, deals damage without creating craters or terrain upheaval, and dissolves conjured blocks harmlessly when turned off. An unreadable or malformed server config stops startup instead of enabling destructive defaults.

The server config groups each skill's settings together and tidies older files automatically on load. Cutscene poses keep working with other animation mods such as Emotecraft, Better Combat and Not Enough Animations. Client graphics settings are in `config/godwand-client.json`. [Dedicated-server verification and limits](docs/DEDICATED_SERVER.md) describes what has actually been tested.

## Single-skill wands

Admins can hold a God Wand in their main hand and use `/godwand lock block_dominion` to restrict that wand to one skill. All skill names: `spatial_path`, `singularity`, `earth_drill`, `supernova`, `stone_cannon`, `atomic`, `fuga`, `silent_gate`, `purple`, `block_dominion`. Use `/godwand unlock` to restore skill selection.

The restriction stays on the item when saved, copied or traded. It does not change other wands. Commands require operator permission level 2 (`/godwand reset_cooldowns [players]`, which clears every skill cooldown, requires level 4); finish or cancel casting before using them. Locked wands show their skill in the tooltip and do not open the skill wheel.

## Build

All ten supported builds come from canonical `common/` sources plus explicit API adapters. One root command checks and collects every target. See [shared source ownership](docs/SHARED_SOURCES.md) before making changes.

Run from this repository root:

```powershell
# Build and check all ten variants, then collect their installable jars
.\gradlew.bat releaseJars

# Build only Fabric/NeoForge 1.21.1
.\gradlew.bat build

# Start Fabric 1.21.1 for manual testing
.\gradlew.bat :fabric:runClient
```

On Linux/macOS use `./gradlew`. Outputs from `releaseJars` are in `build/release/`. Install JDK 17, 21 and 25 for the complete matrix. The wrapper automatically selects the installed JDK 25 to run the build; this does not change the Java version needed to play each Minecraft version. See the development guide if Gradle cannot find an installed JDK.

See [development](docs/DEVELOPMENT.md) for individual builds and tests, and [shared-source ownership](docs/SHARED_SOURCES.md) for where to make changes. The root aggregate also invokes the Java 25 build for Fabric/NeoForge 26.1.2 and 26.3.

## Release status

1.7.0 adds **Block Dominion**, the tenth skill, with sculpted Block Wings flight, Seismic Hero Landing terrain upheaval, 3-tier Meteor Dive, Monolith Cage execution, passive Shield Parry, full zero-grief Safe Mode, and shaderpack-friendly volumetric visuals. It includes all 1.6.x fixes (including 1.6.3 shaderpack/OptiFine detection, Spatial Path falling-block safety, and Silent Gate CPU optimization). Update both server and clients together. See the [changelog](CHANGELOG.md).

All ten version/loader targets pass automated build and test checks, and flagship targets (Forge 1.20.1, NeoForge 1.21.1, and NeoForge 26.3) have additionally been verified via live in-game client QA. See [boss compatibility and limits](docs/BOSS_COMPATIBILITY.md).

## License

* **Java source and Java compatibility adapters:** Licensed under the [MIT License](LICENSE).
* **Assets & Audio (`**/src/main/resources/assets/` and top-level `assets/`):** Custom sound effects (SFX), audio arrangements, visual assets, and mod branding are **All Rights Reserved**. Extracting or reusing these assets in standalone projects without explicit permission is prohibited.
* **Font:** The bundled *Bebas Neue* font retains its original [SIL Open Font License (OFL)](https://openfontlicense.org/).
* **Branding:** The MIT License does not grant permission to use the God Wand name, logos or official branding. Forks must use their own assets and branding and must not present themselves as official releases. Accurate references for compatibility or attribution are fine. See [LICENSE](LICENSE).
* **Distribution & Modpacks:**
  * **Modpacks:** Permitted, provided the modpack references the official CurseForge/Modrinth release and does not monetize this mod directly.
  * **Third-Party Re-uploads:** Standalone redistribution of compiled `.jar` files on third-party websites or mod platforms (CurseForge, Modrinth, 9Minecraft, etc.) without explicit permission is strictly prohibited.


These paths cover every loader/version module. Restrictions on bundled assets do not restrict the MIT permissions for Java code. Earlier published copies retain their applicable license grants. See [LICENSE](LICENSE) for the complete terms.

## Contributors

Thanks to [KingIronMan2011](https://github.com/KingIronMan2011) for build modernization, the Minecraft 26.1.2 port, Iris compatibility and bundled icon support ([#2](https://github.com/HyIsNoob/god-wand/pull/2), [#4](https://github.com/HyIsNoob/god-wand/pull/4), [#5](https://github.com/HyIsNoob/god-wand/pull/5), [#6](https://github.com/HyIsNoob/god-wand/pull/6)).
