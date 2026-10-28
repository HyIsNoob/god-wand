# Changelog

## [1.3.2]

### Added
- Fabric 26.3 test build.
- Damage and cooldown settings for individual skills and charge levels.
- A setting to disable Supernova's instant kills.
- The wand now deals 5 damage with normal attacks. All appearances share this damage.

### Fixed
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
