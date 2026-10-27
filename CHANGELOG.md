# Changelog

All notable changes to the God Wand mod are documented here.

## [1.3.0] - Unreleased

### Added
- Server-configurable Atomic center damage (atomicDamage, default9999) and outgoing pressure-wave damage (atomicShockwaveDamage, default15 per hit). Primary distance falloff and the existing pressure hit cadence remain; zero disables the corresponding damage, with finite validated limits.
- Random rising Atomic mote emitters increased to1,536 with slightly brighter sprites; trail/ripple density stays bounded. The initial source sigil grows radially out from the caster before feeding the ground traces.
- Interactive Atomic release: hold through ATOMIC, arm snap and complete compression; wait in a darkened shot with subtle arm/staff/camera idle and a sustained hum, then release for a quick flash, impact and detonation. A small release hint appears after three seconds. Server blast, damage and protection follow the synchronized held/release clock. Early committed release is buffered through the short dark beat; early charging release still cancels.
- Atomic head halo simplified to one horizontal ring. Random rising mote emitters doubled to1,024; fewer redundant glow layers/trails keep the sampled mesh below its existing cap.
- Fine violet circuit material on fortress wall and tower faces, with angled trace junctions and luminous nodes; shares the round-particle atlas and existing draw batch.
- Sixth skill: I am Atomic, with a nineteen-second fortress buildup and a player-controlled release after ATOMIC.
- Caster movement locks during the cinematic, including jump and flight input; cancellation, death and the final shoulder return release the lock. Incoming damage is rejected during the cast and normal damage resumes afterward.
- Activation thrust with both arms forward, one mild shake, a source sigil, branching ground circuits and rising particles. Rendered poses interpolate between ticks; the final stance turns the torso and cants the wand while retaining normal straight legs. No animation library is added.
- Ground-trace POV trails the branching filaments and rises slowly. A vertical overhead shot catches the roof junction closing, followed by a fast close cage sweep and a continuous I AM / ATOMIC dolly. Passing pressure rings add separate mild camera impulses without extra impact filters. Three principal blast shots lead into an oblique aftermath view and shoulder return.
- Violet energy fortress with eight pointed watchtowers, crenellated walls and arch windows, holographic rings, halo shockwave, procedural volumetric dust and original synthesized charge/blast audio. ATOMIC, an outward arm snap after an inward sweep, a frontal camera push, halo and letterbox share one cue, and immediate inward compression, followed by a dark held pause and a0.4-second release-to-detonation beat. Far-side dust is occluded by the solid light column. No floating terrain blocks or vanilla smoke.
- Only a light column expands to a 98-block radius with its destructive front, then contracts in width while retaining its 1,024-block height. Violet circuit imprints cover the full blast footprint, remain for 30 seconds after the cutscene and fade over three seconds, or clear when the caster starts another Atomic. Scar rendering does not replace foundation blocks.
- A four-second fortress orbit, with synchronized later cues and charge audio. The first trace cut stays direct; soft violet fades apply to the later buildup camera cuts; explosion cuts retain their existing timing. Repeating circular electric waves expand from the caster, then climb around the fortress walls and taper along the pointed tower roofs. A denser random round mote field pauses before ATOMIC, then bursts radially outward with the arms. Buildup head glances are smooth and slightly raised; head/body face the camera at ATOMIC without rewriting player rotation. Blackbars disappear immediately at detonation.
- The roof junction emits an outward circular wave when its circuits close. Blast shake leads the light onset; pressure-ring impulses lead the luminous fronts slightly. Outward release energy stays readable during the simultaneous inward collapse.
- Depth-aware Atomic shader and reduced-resolution bloom. Scene impact filters occur only at activation and ATOMIC.
- Current Magitech wand model and holding transforms brought forward from the released model work.
- Server-authoritative radial damage and budgeted crater carving preserve containers, fluids, unbreakable blocks, active spatial displacement and a small caster pedestal.
- Client `atomicCutscene` setting and server `atomicTerrainRadius` setting (0 disables Atomic terrain damage).

- More numerous, smaller, round violet energy motes rise vertically at random locations with soft glow, trails and ground emitter ripples; eight separate energy ribbons converge above the head during I AM. ATOMIC releases one circular ground shockwave with a mild single shake.

### Fixed
- Atomic leaves both legs in the normal straight stance; removed custom leg crossing, rotation and step offsets.
- Atomic ground circuits and the shader volume collapse into the caster feet instead of rising to waist height. Violet line cores and channel-preserving grading reduce the shaderpack color shift; brighter roof circuits build during the overhead shot and finish early enough to see the junction wave before the cut.
- Outgoing ground pressure rings now damage mobs along their visible path outside the column (8 health points per hit, up to 130 blocks, ten-tick hit cooldown). They exclude players and do not extend terrain destruction.
- The distant Atomic camera adapts its ground framing to the actual loaded radius, including the normal eight-chunk setting. It chooses a clear direction and keeps the planned drift stable around terrain, rather than being pulled into the column or forced into a high overhead view.
- Charge geometry and its shader volume collapse rapidly with inward rings after the release, and continue shrinking all the way into the caster before detonation.
- Aftermath remains visible after camera restoration, including with post effects disabled.
- The optional QA probe now restores its temporary shader setting and runs only through its explicit init script, so ordinary runClient launches do not load it.

This branch targets Fabric/NeoForge 1.21.1. The 1.20.1 port belongs to the separate 1.2.0 worktree.

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
