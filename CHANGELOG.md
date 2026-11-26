# Changelog

## [1.7.0]

### New skill: Block Dominion

- **Telekinetic block armada:** Press **Shift + Left-click** to activate and gather up to 48 orbiting blocks (configurable via `blockDominionCapacity`). Left-click a block to lift it, or left-click the air for **Earthen Surge** to pull up to 24 blocks from the ground at once.
- **Sculpted Block Wings:** **Double-tap Space** (with 6+ blocks) to form layered wings with tight feathered geometry, luminous wingtip contrails, Hover/Glide switching (**Shift + Space**), boost (**Ctrl / Space**), and **Orbital Volley** (**Shift + Right-click**).
- **Seismic Hero Landing & 3-Tier Meteor Dive:** Touching down from flight sends a shockwave that heaves nearby terrain into the air before slamming it back down. Diving straight down progressively folds your wings tighter and builds across three impact tiers up to a volcanic **Cataclysm** strike.
- **Monolith Cage:** Left-click a mob (8 blocks, up to 3 active cages) to immobilize it inside a steady stone cage and impale it from six angles with converging block lances.
- **Railgun Shot, Volley & Shield Parry:** Right-click to fire single high-velocity blocks, hold right-click for a continuous volley, and let orbiting blocks automatically intercept incoming attacks (block cost scales with incoming damage at 1 block per 8 damage, shattering into Guard Break on overwhelming hits; deflected projectiles reflect once and shatter cleanly against a second shield).
- **Zero-grief Safe Mode:** Turning off terrain damage (`terrainDamage: false` or `blockDominionTerrainDamage: false`) prevents Block Dominion from breaking or mining world blocks — it conjures virtual blocks instead, deals damage without craters or terrain upheaval, and dissolves conjured blocks harmlessly when turned off.

### Improvements

- **Clean HUD & Controls Guide:** Added a compact Block Dominion reserve gauge in the bottom-right corner and a minimal vanilla-styled Controls Guide in the bottom-left corner (below chat), with separate switches in the settings menu (**K**) for HUD (`skillHud`) and Controls Guide (`dominionGuide`).
- **Shaderpack & MC 26.3 polish:** Block Dominion blocks and volumetric effects render cleanly under Iris, Oculus and OptiFine shaderpacks, and the Minecraft 26.3 SPIR-V shader pipelines and NeoForge 26.3 metadata load with zero warnings.
- **Config, commands & advancements:** Added `blockDominion` server settings (cooldown, capacity, damage, blast/crater radius, projectile speed, terrain damage), `/godwand lock block_dominion`, and the **Sovereign of Stone** advancement.
- Includes all fixes and optimizations from **1.6.3**.

## [1.6.3]

### Fixed

- With a shaderpack, Fuga and Silent Gate could lose parts of their effects (missing layers, Fuga's bridge gone) when the mod's post effects were switched off or failed to load.
- OptiFine shaders are now detected like Iris/Oculus shaderpacks.
- Spatial Path no longer loses sand, gravel and other falling blocks: they used to fall as the path closed behind you instead of returning to their place.
- Silent Gate is lighter on the CPU: its scene is built once per frame instead of once for each of its layers, so the barrage runs at a higher frame rate. It looks exactly the same.

## [1.6.2]

### Added

- Controller support: two new keybinds, **Next skill** and **Previous skill** (Options → Controls → Key Binds → God Wand). They are unbound by default; bind them to keys, or to gamepad buttons with a controller mod such as Controlify or Controllable. They skip skills the server has disabled and do nothing while the skill is locked.

### Fixed

- Blue can now be cast with a controller's attack button (Controllable).

## [1.6.1]

### Fixed

- Holding Blue, then pressing Red and letting go of both before the Purple shot formed, skipped Blue's cooldown.

## [1.6.0]

### Important: pets are now safe from spells

- By default, spells no longer hurt or push tamed animals, mobs with a name tag or mobs on a lead. Everything else is hit as before.
- Server owners can choose: protect pets (default), hit everything, or hit monsters only.

### New skill: Purple

- **Blue (hold left-click):** a gravity orb that follows your aim and crushes what it pulls in. Once it is ready, let go to throw it.
- **Red (hold right-click):** charge and release a red blast that blows enemies away and tunnels through terrain.
- **A fully charged Red into a ready Blue:** they merge into Purple, with its own cutscene, and a huge sphere erases everything in its path. Works across players.
- **Any other Red into a Blue:** a smaller Purple explodes on the spot.
- **Hold both buttons:** form a purple sphere in front of you and let go to fire it.
- Blue and Red each have their own cooldown, and the cooldown line names which one is cooling down. Purple puts both on cooldown.

### Improvements

- Advancements: one for each skill, plus a few for Blue, Red and Purple.
- One switch in the settings menu (K) turns all cutscenes off or on. Each cutscene still has its own switch.
- New in the settings menu (K): a volume slider for the mod's sounds, the Blue/Red rings around the crosshair, casting Purple and Fuga bare-handed, and an optional third-person view while casting Blue and Red.
- New server settings: one switch to turn off terrain damage for every skill, PvP on or off, and which creatures spells hit.
- Server owners can take skills out of the wand (`disabledSkills`, e.g. `["fuga", "atomic"]`): they disappear from the skill wheel and cannot be cast.

## [1.5.1]

### Fixed

- Silent Gate's floating wand no longer forces the rest of the world to draw early while it is on screen. This could make nearby creatures show up with the wrong look on some setups.

## [1.5.0]

### New skill: Silent Gate

- Raise the wand and a giant magic circle draws itself across the sky. A winged gate comes down through a crack in it, and when its doors open, hundreds of small magic circles fill the sky.
- Right-click to give the command (or wait a few seconds and the gate fires by itself): the circles rain beams of light on every monster around you, and a final golden beam finishes off the strongest one.
- Comes with its own cutscene. You can turn the cutscene off in the settings menu (K).

### Improvements

- Cutscene animations now keep working with other animation mods installed, such as Emotecraft, Better Combat and Not Enough Animations. Yes Steve Model players keep their model's own animations, and the cutscene still plays.
- The server settings file is shorter and easier to read: each skill's settings are grouped together and the level lists sit on one line. Your existing settings are kept and tidied automatically.
- New server settings for Silent Gate: cooldown, damage, which creatures it attacks, range, how long the attack lasts, how long the gate waits for your command, how many magic circles appear, and crater size (or no terrain damage at all).
- The "preserve terrain" presets now also stop Silent Gate from making craters.

### Fixed

- Includes all fixes from 1.4.1 to 1.4.4.

## [1.4.4]

### Fixed

- Fuga's configured radius now limits damage, shockwave, slowness and terrain changes around the impact. Its shockwave previously reached up to 230 blocks regardless of the radius setting.
- Fuga no longer places spreading fire blocks.

### Added

- Server options for Fuga shockwave damage, entity ignition, knockback and slowness. Visual effects remain fully available.

## [1.4.3]

### Fixed

- Fuga block damage is disabled by both terrain preservation presets. Reopening a single-player world now picks up changes to the server config.

## [1.4.2]

### Fixed

- Fixed a crash when opening the configuration menu on Minecraft 26.3 and 1.21.11 (Fabric and NeoForge).

## [1.4.1]

### Added

- New admin command `/godwand reset_cooldowns [players]` (operator level 4) clears every skill cooldown instantly.

### Fixed

- `/godwand lock fuga` now works: a wand can be locked to Fuga like the other skills.
- Fuga's cutscene, phoenix and meteor no longer vanish for players on servers with higher ping or a lagging server; the blast still happened, but the visuals were dropped.
- Mobs burning and dying in Fuga's blast are quieter, and stay quieter even when the cutscene is turned off or skipped.
- Fixed Fuga's arrow, phoenix, meteor and other mesh effects being invisible on Fabric for Minecraft 1.21.11.
- The God Wand recipe now unlocks in the recipe book on Minecraft 1.21.11 and 26.1.2 (its advancement failed to load and logged an error).

## [1.4.0]

### Added

- Fuga, the seventh skill: gather fire between your palms, draw a burning bow and release a fire arrow that turns into a gliding phoenix. When it hits, the phoenix rises into the sky, a blazing meteor falls and a pillar of fire erupts.
- Fuga has its own cutscene (you can turn it off in the settings) and its own soundtrack. Server owners can change its damage, cooldown and terrain destruction.
- Enemies caught under the falling meteor are slowed until it explodes.
- Simplified Chinese language.
- Builds for Minecraft 26.1.2 on Fabric and NeoForge.
- God Wand now shows its icon in the mod list.

### Changed

- Better compatibility with Iris and Oculus shaderpacks.
- Better performance when skills wipe out large groups of mobs: their experience drops are merged into fewer, larger orbs, and you still get the same amount of experience.
- Fuga's shockwaves now follow the ground over hills, walls and craters.
- Other sounds are quieter during Fuga's explosion, so the finale stays clear.
- Spatial Path and Stone Cannon messages are now translated, and the wand tooltip lists all seven skills.

### Fixed

- Spatial Path no longer destroys cushions and attached decorations.
- Fixed a camera glitch after visual effects reset on Minecraft 26.1.2.

## [1.3.5]

### Changed

- Replaced Atomic, Earth Drill and Supernova SFX with the owner-provided CC0-only mixes.
- Updated the second impact frame with the owner-created Filmora image.
- Improved Atomic terrain coverage on normal worlds.
- Reduced Atomic crater depth to prioritize wider terrain coverage. Visual effects are unchanged.
- Atomic now levels high terrain first instead of leaving tall ridges across its crater when terrain work stops.

### Fixed

- Fixed Singularity rays remaining after death and collapse, including unloaded areas.
- Fixed skills staying unavailable after dying and respawning, including when another skill was selected during cooldown.
- Inactive cinematic effects no longer block other mods from adjusting zoom.
- Released Earth Drill and Supernova blocks no longer disappear when the caster dies.
- Fixed displaced spell blocks remaining in the air after dying and reloading their area.
- Ongoing spells no longer pull in or damage their caster after respawning.
- Supernova no longer leaves displaced blocks that can return inside its blast.

## [1.3.4]

This release includes all eight supported builds.

### Added

- First public release for Minecraft 1.21.11 on Fabric and NeoForge.
- Spatial Path now pushes away other players as well as mobs.
- Admin commands to lock each wand to one skill and unlock it again.

### Fixed

- Fixed distorted screens during impact effects in Minecraft 1.21.11.
- Fixed missing surfaces on wand models in Minecraft 1.21.11.

## [1.3.3]

### Added

- Separate cooldowns for each skill.

### Changed

- Longer cooldowns at higher charge levels. Damage is unchanged.
- Existing stock cooldowns update automatically; custom settings are preserved.

### Fixed

- Atomic now destroys blocks within its expanding light column.
- Atomic terrain destruction expands in a circle instead of square layers.

## [1.3.2]

### Added

- Config: Damage and cooldown settings for individual skills and charge levels.
- Config: A setting to disable Supernova's instant kills.
- The wand now deals 5 damage with normal attacks. All appearances share this damage.

### Improved

- Wider Atomic blast view and stronger explosion shake.
- Atomic destroys terrain across a 50% larger radius.
- Smoother, rounder Singularity charge orb with a brighter core and soft glow.
- Pixel-art skill wheel with simple backgrounds and highlighted selection areas.
- Removed loose particles while charging Singularity.
- Increased Atomic terrain clearing speed.
- Reduced repeated terrain checks and temporary allocations in Atomic rendering.
- Atomic plans terrain clearing in the background and spreads block removal across short server steps.
- Atomic's crater can reach 30 blocks deep.

### Fixed

- Fixed a Fabric 26.3 crash when drawing skill effects.
- Fixed missing spell projectiles and Atomic effects in 26.3.
- Fixed misplaced and undersized wand models in 26.3.
- Atomic stops breaking blocks before its column starts shrinking.
- Atomic now destroys campfires, banners, chests and machines inside its terrain range.
- Improved spell damage against modded bosses, including boss damage caps.
- Added hit support for Desert Behemoths sandworm segments.
- Atomic now clears lava as well as water inside its crater.
- Prevented full-turn head blending during Atomic.
- Atomic now retries rejected hits instead of permanently skipping the target.
- Earth Drill now respects zero damage settings for its final slam.

## [1.3.1]

### Fixed

- Atomic now clears water inside its crater instead of leaving it floating after the ground is destroyed.

## [1.3.0]

### Added

- New skill: **I am Atomic**, with a full cutscene and player-controlled release.
- Custom Atomic sound effects and heartbeat audio while holding.
- **Aqua Heartia** and **Shadow Blade** appearances, with automatic switching by skill.
- **K** settings menu for wand appearance and visual effects.
- More settings for damage, cooldowns and terrain destruction, including damage-only spells.

### Improved

- Atomic's camera can pass through blocks. Skipping its camera after detonation restores movement.
- Reworked the **V** skill selection menu.
- Refined wand models, holding poses and spell effects.

### Fixed

- Older settings files now gain new options without resetting your choices.
- Added missing crafting recipes in the two Minecraft 1.20.1 versions.
- Fixed Atomic camera recovery after switching away from the game.
- Fixed gaps beneath Atomic's light column and improved its distant camera view.
- Fixed several block restoration and visual issues.

### Supported versions

- **1.21.1:** Fabric and NeoForge.
- **1.20.1:** Fabric and Forge.

---

## [1.1.1] - 2026-10-24

### Fixed

- Fixed visual aura/outline appearing around terrain and trees during Skill 4 (Supernova), even when playing without shaders. Einstein Ring, violet halo, and stellar flare are now strictly occluded by foreground blocks instead of bleeding over them.
- Suppressed glowing tags and outlines across all display entities (including singularity tether beams).

---

## [1.1.0] - 2026-10-24

### Added

- NeoForge 1.21.1 support: The mod now runs on both Fabric and NeoForge.
- Skill 1 (Spatial Path): Added a smooth expanding shockwave animation when activating the skill.
- Skill 2 (Singularity): Added a rotating energy orb in front of the wand while charging, with particles swirling inward as you charge.

### Changed & Balanced

- Skill 2 (Singularity): Regular mobs under 50 HP are crushed immediately when the orb collapses. Bosses and high-health mobs (such as the Ender Dragon and Wither) now take heavy burst damage instead of dying instantly.
- Skill 4 (Supernova): Adjusted damage on bosses so they take massive damage instead of being instantly deleted, making boss fights much more fun.

### Fixed

- Fixed an issue where dying with Skill 1 active caused floating blocks to stay permanently stuck. The skill now resets cleanly on death and restores all blocks.
- Fixed bright purple outlines appearing around blocks and trees when playing with shaderpacks (such as Complementary).
- Fixed sharp lines cutting across the screen and player hand when standing close to the black hole effect.

---

## [1.0.0] - 2026-10-21

### Initial Release

- Added the God Wand with 5 unique skills:
  - Skill 1 (Spatial Path): Walk straight through mountains (the path closes behind you), form temporary bridges across gaps, and block incoming enemy projectiles.
  - Skill 2 (Singularity): Lift ground blocks and mobs into the air, with early collapse to crush enemies. Restores terrain completely.
  - Skill 3 (Earth Drill): Pull up boulders into a spinning drill and slam the ground to create a crater.
  - Skill 4 (Supernova): Long-range charged spell that opens a black hole and detonates in a massive explosion.
  - Skill 5 (Stone Cannon): Charge a rock drill in front of you and fire a high-speed piercing projectile.
- Added quick skill selection wheel (hold V by default).
- Added survival crafting recipe in the standard Crafting Table.
- Added dedicated creative tab for God Wand.
