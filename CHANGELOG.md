# Changelog

## [1.3.0] — I am Atomic & More Control

### New skill: I am Atomic

The sixth skill turns casting into a full scene. Hold right-click to build your domain. Keep holding through **I AM**, the sword sweep and **ATOMIC**. Once the screen goes dark, the spell waits for you. Release right-click to detonate.

- A growing circle beneath your feet sends branching purple lines across the ground.
- Those lines form a fortress with walls, windows, eight pointed towers and a closing roof.
- Bright waves travel outward from you and climb the fortress.
- Small purple particles rise throughout the domain.
- Energy from above and from the ground gathers into your sword. Its blade turns purple as it charges.
- Your character holds the sword upright in front of their face, then sweeps it aside before spreading their arms.
- The sword leaves a fading light trail during the sweep.
- **I AM** appears during the buildup. **ATOMIC** appears with the arm spread, an outward shockwave and a camera pullback.
- The fortress compresses immediately with ATOMIC. Inward rings follow it into the center.
- The dark pause waits for your release. Small idle movements and repeating heartbeats keep the hold from becoming a still image.
- A release reminder appears if you keep holding for three seconds.
- Releasing triggers a quick flash, a brief impact frame and the blast.
- Letting go before the initial charge finishes cancels the cast.

### Atomic cameras and effects

- A low shot follows the spreading ground lines before rising into the overhead view.
- The overhead shot catches the roof lines meeting and the circular wave that follows.
- A close pass around the fortress shows its walls and towers before returning to the caster.
- Later buildup cuts use short purple fades. The opening cut stays direct.
- The blast uses fewer, longer shots so its growth is easier to follow.
- Low, overhead and distant views show the column, its expanding front and the crater.
- The camera returns over the player's shoulder after the blast.
- Black bars appear for ATOMIC and disappear when the explosion begins.
- Camera shake marks the release and passing shockwaves.
- Purple wind spreads outward continuously during the blast, then fades as the column contracts.
- Removed the repeated brightness flicker during the explosion.
- The blast grows as a tall column rather than a sphere. It shrinks in width without shortening.
- Ground marks cover the blast area, remain for 30 seconds after the scene and then fade. Casting Atomic again clears your previous marks.
- The fortress walls have fine glowing patterns rather than plain surfaces.
- Atomic has its own effects and remains visible without an extra shaderpack.

### Atomic damage and casting

- The column can reach a radius of 98 blocks: up to 196 blocks across.
- Its crater can reach up to 28 blocks below the casting ground, becoming shallower toward the edge.
- Default damage is 9,999 at the center and decreases with distance. This is damage, rather than a promise to instantly kill every enemy.
- Outer shockwaves deal 15 damage per successful hit to mobs they pass through. They do not expand the crater beyond the main blast.
- Movement is locked during the cast, and incoming damage is blocked while casting.
- Normal movement and damage return afterward.
- Losing the wand, changing dimensions, dying or disconnecting cancels an unfinished cast.
- You can skip Atomic's camera with Esc or F5.

### New Atomic sound effects

- Added a custom track for the buildup and a separate track for release and explosion.
- The buildup track starts when casting begins and plays to its end, including across the hold.
- The explosion track starts when you release right-click and plays to its end, including its final echo after the camera returns.
- Canceling the cast or leaving the world stops its sounds.
- Holding uses Minecraft heartbeats with slightly higher volume.
- Removed the temporary extra chimes and background hum so they do not play over the new tracks.

### Skill selection and wand appearance

- Reworked the **V** skill selection menu for the six skills.
- Added a **K** settings menu for appearance and visual effects.
- Choose between **Eclipse**, **Aqua Heartia** and **Shadow Blade**.
- **AUTO** uses Aqua Heartia for Earth Drill and Stone Cannon, Shadow Blade for Atomic, and Eclipse for the other skills.
- A manual choice keeps your selected appearance across skills.
- Reworked Shadow Blade's shape, edges, guard, handle and holding position.
- Atomic's sword pose is separate from the normal holding pose.
- Legs keep their normal stance during Atomic.

### More control over world damage

Server owners and modpack makers can decide which spells may change the terrain.

- **Earth Drill:** choose whether it gathers blocks and whether it leaves permanent destruction. Keep the lift but restore the blocks afterward, or turn both off.
- **Supernova:** choose whether it pulls blocks into the core and whether the explosion destroys terrain. It can deal damage without carving a crater.
- **Stone Cannon:** turn off block destruction on impact.
- **Atomic:** turn off terrain destruction while keeping the damage and scene.
- Adjust Atomic's main damage and outer shockwave damage separately.
- Adjust overall damage, cooldowns and the available terrain ranges.
- Disable spell use in selected dimensions.
- Terrain destruction remains enabled by default for the destructive skills. Change these settings before testing near a build you want to keep.
- These rules are controlled by the server. The K menu handles your own visual settings.

### Fixes

- Extended the light column down to the crater floor so it no longer appears to float above the hole. The outward wind and shockwaves keep their original height.

- Older settings files now gain missing options automatically. Your existing choices are kept; deleting the file is no longer needed.
- Broken server settings no longer cause the mod to quietly turn destructive defaults back on. The file must be corrected before startup continues.
- Temporary block restoration avoids replacing a new block or container placed in the original spot. Removing the obstruction allows the saved block to return.
- Fixed missing crafting recipes and recipe-book unlocks in the two Minecraft 1.20.1 versions.
- Returning after switching away from the game no longer leaves future Atomic casts stuck without their camera.
- Adjusted the distant Atomic view for shorter viewing distances so the foot of the column stays in frame more consistently.
- Fixed ground energy rising toward the caster's waist during compression. It now collapses toward the feet.
- Energy lines detach from the sword as the sideways sweep begins, leaving a trail instead of stretched connections.
- Brightened the roof lines so their closing motion is easier to see.
- Reduced unwanted purple color changes with shaderpacks.
- Corrected effects showing through the far side of the light column.
- Kept outline changes limited to blocks moved by God Wand, so unrelated glowing objects retain their outlines.

### Available versions

- **Minecraft 1.21.1:** Fabric and NeoForge.
- **Minecraft 1.20.1:** Fabric and Forge.
- All four versions include the six skills, wand appearances, settings and crafting recipe.
- Install the matching version on both your game and server. Fabric also requires Fabric API.

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
