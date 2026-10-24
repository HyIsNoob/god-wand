# Visual/combat acceptance

## Stone Cannon long wind and pressure burst

- Charge wind uses two broad ribbons, with a tail reaching 2.15-4.65 blocks
  behind the player along the aiming axis. Rotation accelerates continuously;
  the envelope tightens and brightens during charge. Its rotation is independent
  of the stone's stepped charge-level spin. The camera-plane crossing is softened.
- On impact, three bands form a short cone opening back from the collision along
  the incoming shot axis. It expands and fades over eight ticks (0.4 seconds).
  Damage, collision handling and excavation are unchanged.
- Test levels 1-4 in first person and F5, looking level/up/down, rotating while
  charging and canceling. Check the broad ribbons do not hide the aiming area.
  Hit walls, ground and mobs; the cone should appear once and disappear cleanly.
  Check another client and disconnect/dimension changes. Visual quality and FPS
  in Minecraft remain unverified; automated checks cover the wind envelope and
  short outward-opening impact geometry.

## Current review: Singularity visual pass (2026-10-23)

- Skill 2 mechanics, damage, block motion and restoration timing are unchanged.
  Existing thin threads now carry two staggered client-rendered light trails
  toward the core. No new display entities are created for these trails.
- The deployed sphere has a stable cyan/violet plasma shell, moving veins, a
  small white-blue center and two tilted orbital bands. Its size grows on arrival
  and contracts on recall; repeated restoration feedback must not regrow it.
- Test all three charge levels, automatic return, early return just after impact
  and early return during hovering. Trails must stay on their threads through
  block interpolation and disappear with them. Check from above/below and from
  behind a wall; neither trails nor the core should render through solid terrain.
- Check another client entering the area mid-cast, dimension changes and
  disconnect/rejoin. Compare FPS while orbiting a fully expanded level-3 cast.
- Automated checks cover inward flow, bounded recall, packet round trips/limits,
  stable core size and shrinking continuation. GPU checks cover shader linking,
  animated shell, wall occlusion and the existing aura behavior using a synthetic
  scene. In-game visuals, client/server tracking and FPS remain unverified.
- Baseline source/JAR: `.local-backups/20261023-161702-singularity-visual/`.

## Current review: Spatial Path refraction and slimmer wand head (2026-10-23)

- This earlier pass changes Skill 1 visuals and the item model. Skills 3-5 are
  waiting for separate visual reviews.
- Aura refraction follows the 3.3-block shell, flows upward, fades toward the
  owner's view direction and checks scene depth. Stand behind a wall: the wall
  must remain undistorted. From inside, the crosshair area should remain clear;
  from outside, the curved edge should subtly bend the scenery.
- Compare first/third person, sprinting, turning and toggling off. Check FPS with
  the field on/off. Refraction uses the nearest active aura; other players' ribbon
  geometry still renders normally.
- The wand head has a smaller pedestal, shorter supports and a stepped crystal.
  Inspect inventory, both hands, charging and third person for clipping.
- Baseline source, JAR and model preview are retained in
  `.local-backups/20261023-160423-aura-wand/`. Its `README.txt` describes restoration.
- GPU checks use a synthetic grid/depth scene; they do not validate Minecraft
  camera integration, modpack compatibility, FPS or final visual quality.

Suggested next passes, one at a time: Skill 2 moving light pulses along the
existing threads; Skill 3 heat cracks and ember trails tied to rotational speed;
Skill 5 charge-dependent air compression and a brief conical impact wake;
Skill 4 staggered curved arcs across the expanding spherical shell. Review each
in game before implementing the next.

Automated: `./gradlew.bat test build` checks compilation, packaging, the aura's
minimum lift, permanent-restoration guard, beam endpoints and swept drill geometry.
These checks do not replace an in-game render or combat test.

Use a disposable world: skills 3, 4 and 5 permanently destroy terrain.

- Skill 1: walk, sprint and turn 90/180 degrees inside a stone hillside. Outer
  blocks should remain lifted; nearby blocks should follow turns without a long
  opening pause. Disable the skill and verify the original blocks return.
- Skill 2: check thin purple tethers during expansion, hovering, automatic recall
  and early recall. No tethers should remain after restoration or disconnect.
- Skill 3: check white/cyan helical wind and expanding pressure arcs as the drill
  forms, accelerates and dives. Terrain must remain destroyed after disabling
  skill 1 nearby or restarting the world. Include a two-block door in the test.
- Skill 5: compare charge levels 1–4 in first/third person and from another client.
  Wind should originate at the nose. Shoot horizontally, diagonally and vertically
  through stone: the projectile detonates at its first block/mob collision and leaves a bounded
  drill-shaped cavity. It must not excavate along the flight path. No terrain should restore later.
- Combat: compare the same unarmored mob before/after for skills 2, 3 and 5;
  mob damage is doubled, player damage retains its previous values. A stone cannon
  directly deals 130 / 240 / 370 / 520 before mitigation. Test Supernova against
  ordinary mobs, armored mobs, Wither and Ender Dragon; its lethal fallback is
  intended to defeat armor and boss damage caps. Other mods' death overrides need
  testing in the actual modpack.

In-game checks above have not been executed by this change's automated suite.

## Supernova performance and burst revision

- Terrain/damage radius: 96 blocks (previously 125); nominal crater depth: 44
  (previously 65), with the existing irregular floor retained.
- Display limits remain 1,800 during suction, 650 flung displays and 200 falling
  debris blocks. The visual fireball still grows to radius 98.
- Crater excavation now commits once at detonation, with no further crater writes
  during aftermath. This can produce a server hitch on dense terrain; it has not
  been benchmarked in game.
- The initial burst has a stable expanding shell. Turbulence fades in between
  22% and 40% of the visual effect, with a short initial camera impact.
- Check the explosion, flying debris and completed crater on the same seed before
  and after; measure server tick time separately from client FPS. No in-game FPS
  improvement has been measured yet. Fragment shader compilation was checked on
  the local Radeon RX 5500M through a hidden OpenGL context.

- Drill 3 wind starts at 0.62 rad/tick; heat builds to 0.85, with hot block visuals, flames and a separate cloud vortex above it. These are generated cloud bands, not deformation of vanilla clouds.

## Spatial Path aura

- Toggle on/off and check that the dome disappears on disable, disconnect and
  dimension change; a nearby joining client receives its state within one second.
- Walk under a ceiling 4 blocks above the feet: upper blocks should open and then
  restore, while the footing remains intact.
- Fire normal and spectral arrows from the side and above; incoming arrows should
  bend sideways at the shell, lose speed, and emit a small spark ripple. The
  caster's own projectiles are excluded; hostile tridents are now included. The bounded scan covers arrows
  within 16 blocks; faster modded projectiles require separate compatibility work.
- Rotate the camera: the forward dome fades smoothly for navigation. Check both
  first and third person. The dome is animated geometry, not screen refraction.
- Math tests cover swept intersections, outward deflection, speed loss, upper
  reach and the viewing fade. Actual in-game combat/rendering remains unverified.

## Spatial Path projectile and mob field

- Fire hostile arrows, tridents, small/large fireballs, snowballs and shulker bullets
  through the field. Standard ProjectileEntity subclasses share the same swept
  deflection logic; homing/acceleration behavior needs in-game confirmation.
- The incoming outer zone slows projectiles; the inner shell redirects them and
  reduces explosive projectile acceleration. Ownership remains unchanged.
- Mobs approaching from the sides or above receive an outward velocity floor;
  repeated ticks do not keep adding speed. No mob damage is dealt by this field.
  Entities sharing the player's vehicle are excluded.
- This is an approach barrier, not damage immunity: explosions, melee reach of
  unusual mobs, hazards and nonstandard mod projectiles still need verification.

## Temporary bridge

- Enable Spatial Path, select it and press/release Shift: Bridge on/off. A
  Shift+right-click skill change should not toggle bridge mode on release.
- Face a gap: the plan is three blocks wide, up to three blocks ahead. It borrows
  full solid blocks without inventories or falling-block behavior. Own floating
  aura blocks are preferred, then nearby material, then unoccupied rear bridge.
- Jump at an edge: the deck only rises one block after the feet clear the step.
- Disable while standing on the bridge: support stays until all players move off.
  Also test jumping, slabs, turning and another player standing on the rear deck.
- Save/restart while standing over a void; the bridge journal preserves support
  until its owner returns and moves away. Test mining/exploding a bridge block:
  its original must not be restored as a second copy. An occupied original source
  must not be overwritten during restoration.
- Tests cover three-lane planning, turn alignment, jump thresholds and bridge
  owner/destination journal round trips. Actual world, restart and multiplayer
  scenarios above have not been executed; verify on a disposable world first.

## Recall and drill pacing review

- Skill 2: test level 3 automatic/early recall. Centers follow a smooth path;
  rotation and size unwind around each block center. Final poses settle before
  terrain restoration. Check slabs/stairs and densely overlapping debris.
- Skill 3: formation is 5 blocks higher; completed spin lasts 70 instead of 46
  ticks. Dive, impact and cleanup milestones move together by 24 ticks.
  Crater depths by level are 19/28/41/54 blocks. Test on a disposable world.
- Heat spreads across individual blocks; hot seams and ember trails rotate around
  the drill. Cloud radius is 1.45 times the former size; pressure rings move farther
  upward along the drill during descent. Inspect from the side and below.
- Automated checks do not establish in-game visual quality or FPS.


### Supernova swallow / flare / spherical arcs (2026-10-23)
- Orbit and floating blocks spiral inward during ticks 280-318; core contracts only at 318-330.
- Core disappears before the short lens flare at 332-339. Detonation packet still starts at 339; terrain/damage timing remains 340.
- Explosion starts at radius 14, exceeds 30 during the four anime frames, then expands monotonically. White overlay is translucent and fades in eight ticks.
- Three curved ribbons follow the sphere surface, staggered by 1.5 ticks. Body exposure preserves their contrast.
- Check in game: distant and near views, terrain occlusion, orbital swallowing, flare-to-impact continuity, and FPS at full cast. GPU synthetic previews are not gameplay acceptance.
- Rollback snapshot: `.local-backups/20261023-164409-nova-swallow`.


### Feedback from note.txt / Shooting Star 1.4.4 reference (2026-10-23)
- Spatial Path checks falling entities before they land. Ordinary blocks that land in an already displaced cell are lifted and pushed sideways without replacing its saved original. Protect support blocks as before. Block entities are excluded from this fallback to preserve inventories/NBT.
- Singularity: after expansion ends at tick 22, block transforms stay fixed until recall; no idle bob, tumble, or outward drift.
- Earth Drill: hover height now scales as 18 + radius * 2.2. Wind and cloud reveal smoothly from speed .50 to .60 instead of appearing at full opacity.
- Stone Cannon: retain one-second delay; three wind ribbons then start at 1.2 rad/tick and accelerate to 2.16. Rotation sign and helix handedness match the stone. Stone rotation no longer jumps at charge-level transitions.
- Supernova: faster accretion rotation, textured plasma sphere, staggered surface arcs, two world-space pressure fronts. Surface detail uses world normals. Screen-wide scene bloom/vignette disabled for Supernova; glow is local to the blast.
- Supernova crater: retains radius 96 and commits once. Cache chunk per column, skip repeated loaded checks, mark persistence once. Remove floor painting, lava, and 200 landing FallingBlocks; retain up to 650 visual flying blocks.
- GPU checks: compile/link, existing aura/orb checks, blast moves with camera strafe, pressure waves visible independently from the fireball. Synthetic images under build/previews/aura-nova-*.png are not in-game acceptance.
- Gameplay checks still needed: sand/gravel falling into stationary and moving aura; recall after sand; full tier 3 cloud onset; cannon winding from front/side; Supernova near/far, strafe, occlusion, one-shot excavation and server tick/FPS.
- Snapshot: `.local-backups/20261023-191737-feedback-shooting-star144`.


### Boss-explosion reference pass (2026-10-23)
- Supernova uses a white-hot opening core, pink/red energy body, violet/cyan rim fringes and luminous surface arcs. Core exposure decays quickly enough to reveal body detail after the initial flash.
- Twenty finite world-space ray segments radiate outward over the first 34 ticks; white/gold cores have colored sheaths. Existing flying block displays provide physical debris silhouettes.
- Two pressure fronts have bright white/pink leading edges, red wakes and violet fringes. They remain anchored to the blast.
- Black-hole horizon has an asymmetric gold/white rim, narrow prismatic edge and violet halo.
- Terrain/damage behavior is unchanged by this visual pass. Snapshot: `.local-backups/20261023-192806-nova-boss-reference`.
- Recheck in Minecraft: opening frame, near-ground dome silhouette, surface arcs after peak exposure, ray/debris visibility, wall occlusion, camera strafe, and GPU frame time. Standalone previews do not validate gameplay FPS.


### Final cinematic bloom pass (2026-10-23)
- Dedicated five-stage Black Hole/Supernova pipeline: scene, effect-only emission, horizontal blur, vertical blur/opening shafts, composite. Emission and blur use quarter dimensions; generic skills retain their existing pipeline.
- Exact matrix projection replaces the approximate FOV/vertical-center calculation. Both processors resize and release correctly on disconnect/shutdown.
- Larger black-hole core (visual endpoint 8.5), matched disk swallowing and thinner warm spiral inflow. Explosion reaches radius 98 by tick 96; brightness transitions into a broken purple afterglow.
- Fixed corona and orbit-filament drawing over foreground ground. Local aftermath refraction stays separate from radius growth.
- Supernova's short impact reaches viewers up to 250 blocks; other cinematic events retain their original range.
- Validation: full test/build, aura/orb GPU regression, five-pass GPU render with vanilla vertex shader, wall/ground/strafe/resize/BH-flare assertions, actual Minecraft PostEffectProcessor resource-loading smoke check with no uniform warnings.
- RX 5500M synthetic early-blast medians (3 warmed samples): 1080p 5.21 ms base / 5.69 ms with bloom; 720p 2.34 / 2.59 ms. These are not game FPS measurements.
- Snapshot: `.local-backups/20261023-193413-cinematic-bloom`. Recheck in game at near/far viewpoints with flying debris and actual terrain before claiming final visual acceptance.


### First-person materials / aura render order / two cannon ribbons (2026-10-23)
- Model material buffers are requested in draw order: finish the solid body and silver trim before obtaining the emissive core buffer. This respects Immediate providers that flush their previous shared layer on a material switch. Geometry and view transforms are preserved.
- Added a provider-invalidation regression and first-person core visibility checks for both hands. Existing geometry tests still verify full mesh and attached trim.
- Moved post-processing from AFTER_TRANSLUCENT to LAST, after clouds/weather/world framebuffer writes. Rebind the main framebuffer and restore depth testing/writes in finally before hand/GUI rendering.
- Stone Cannon charge wind is back to two equally spaced ribbons, preserving delayed reveal, direction and acceleration.
- Snapshot: `.local-backups/20261023-221242-firstperson-aura-fixes`.
- In-game checks pending: first-person idle and charge in the user's modpack; aura on/off at horizon with clouds and weather; left-hand model; two wind ribbons at full charge.


## Skill wheel (2026-10-23)
- Hold V with the wand in either hand, aim the pointer into one of five sectors, and release to select. Center and Esc cancel; the game keeps running.
- Key can be rebound in Controls > God Wand. Keyboard and mouse bindings are supported. The wheel cannot open while charging or over another screen.
- Main hand takes priority if both hands hold wands. Changing held stack, death, or lost window focus cancels selection. Server validates mode, held inventory slot, item type and charging state before updating the item.
- Uses the same mode-change feedback as Shift + Right-Click. Opening/selecting does not cast or deactivate skills. Current skill has an inner marker; its shared item cooldown appears on the outer rim.
- Added automated checks for all five directions, dead zone, sector seams and invalid/stale inventory selection requests.
- Validation: 70 JUnit tests and build passed. A standalone GPU check called the actual wheel geometry with Minecraft's GUI shader and back-face culling enabled for all five selections plus the neutral state; fixed reversed arc winding found by that check. Geometry previews are in build/previews/skill-wheel-*.png (no text/world/gameplay).
- Pending gameplay check: V press/drag/release, Esc/focus cancellation, remapped keys/mouse buttons, camera lock restoration, offhand and multiplayer synchronization, GUI scaling and modpack key conflicts.


## Spatial Path aura redesign and wand face overlap (2026-10-23)
- User gameplay screenshots still showed a black horizon band with the previous aura-only post-processing path. Removed that entire path: inactive impacts return before reading the world render context or touching any framebuffer. Removed aura refraction uniforms and shader code as well; Skills 2-5 keep their impact pipeline.
- Replaced the 28 intersecting latitude/longitude ribbons with six short rising curved streams and two broken ascending arcs. Soft alpha edges, pale blue/white colors, regular transparency instead of additive stacking, no closed grid or full-screen tint. The view ahead fades out in first person; ribbons remain depth-tested behind terrain.
- The body and silver trim shared coplanar faces on several collars/spire caps. Raised trim geometry by 0.015 model units to prevent dark/silver z-fighting while keeping the silhouette and item view transforms.
- Validation: 75 JUnit tests passed, including a failing-then-passing test for coplanar dark/silver faces, idle post-processing bypass and aura shell bounds/motion/clear forward view and fade before loop resets. Native GPU tests rendered the actual new aura mesh from outside/inside with a synthetic sky/ground background: visible ribbons, no introduced black pixels, complete occlusion by a foreground depth plane. Impact shader compile/link and orb/nova depth/animation checks passed.
- Previews: build/previews/aura-redesign-0-*.png (outside), aura-redesign-1-*.png (inside), aura-redesign-2-*.png (occluded). These are synthetic render checks, not screenshots from the user's modpack.
- Pending gameplay: confirm horizon in the Sodium/Iris modpack, first/third-person aura appearance, moving/turning views and animated wand trim. The exact third-party rendering interaction behind the old horizon band was not reproduced in a full client.


## Supernova world shear, stronger gravity and detonation order (2026-10-23)
- Added a visual cut through the final black-hole buildup: the two sides of the rendered scene slide along a world-oriented diagonal, then return smoothly within 24 ticks after the burst. It is an image effect; it never moves or restores actual terrain. The real crater remains permanent.
- Reused the final cosmic composite pass and scene alpha for source occlusion. Scene and bloom use the same displaced UVs, with a thin violet/white fracture, faded screen edges and clamped texture reads. No extra render pass or framebuffer; the cut is suppressed when the source is hidden behind a foreground wall or off screen.
- Changed mob gravity from an impulse every six ticks to a velocity pull every tick, increasing toward full charge. Speed is capped at 3.3 blocks/tick and tapers near the core. The caster and existing behavior for other players are unchanged; lethal mob damage stays unchanged.
- Removed the tick-339 early burst broadcast. At tick 340, the crater is committed once, affected loaded chunks flush their pending vanilla updates, then the visual burst is sent. Vanilla update sets are cleared by that flush, avoiding duplicate custom block packets. The black-hole clock now starts after cast preparation and holds its final flare for at most 40 ticks while awaiting the server burst.
- Preserved up to 650 flying block displays, extending their outward flight to 44 ticks and cleaning them up at burst +48. They travel beyond the sphere rim to remain visible after the impact flash.
- Reduced the broad white plasma fill while keeping bright core/arc/pressure edges. Added darker plasma contrast so the sphere reads in daylight. In the synthetic daytime GPU view, all-channel near-white coverage fell from about 21% to 10% at tick 24, and 23% to 6% at tick 48; these are image checks, not gameplay metrics.
- Validation: pull and split-timeline regression tests, bounded final-flare hold, native GPU cut/restoration/wall-occlusion checks, daytime/close-camera/odd-window previews. Native checks use synthetic ground/sky and do not include the flying blocks or a complete Minecraft world.
- Pending gameplay: actual mob suction, crater/explosion alignment with Sodium chunk rebuilding and network latency, source occlusion while moving, debris readability and frame time in the modpack. Server packet ordering is verified in code; exact visible frame synchronization has not been observed in game.
- Backup: .local-backups/20261023-230103-supernova-rift.


## Dense Supernova body (2026-10-23)
- Replaced the translucent additive shell with an opaque volumetric fire/plasma mass. Three bounded samples integrate density front-to-back, terminate once opaque, and stop at scene depth so foreground terrain remains in front. Premultiplied volume color replaces the covered scene rather than merely lighting it.
- Two scales of moving cloud noise and analytic density gradients give the billows lit and shaded faces, hot pockets and a cooling smoke phase. Edges break up with density instead of a perfectly circular neon outline.
- Reduced the surface arcs to short embedded tongues and made the ground pressure fronts fade within 24 ticks. Existing world split, mob gravity, damage, crater timing and flying blocks are unchanged.
- Native GPU regression check: render the same body over two strongly different backgrounds, with identical scene depth. Interior background influence was about 36/255 before the change and below 0.1/255 afterward, without turning the body into a plain white fill. These are synthetic image measurements, not gameplay statistics.
- Previews: build/previews/nova-dense-phase-*.png, nova-dense-close.png and nova-dense-wall.png. GPU checks cover opacity, texture variation, foreground occlusion, animation phases and odd/tiny target sizes. A native Minecraft post-processor load check and the existing pipeline checks are also used.
- Pending gameplay: motion/readability of the dense mass with real terrain and debris, and frame rate inside the modpack. Synthetic previews do not validate the complete in-game scene.
- Backup: .local-backups/20261023-231720-dense-supernova.


## Supernova impact before terrain work (2026-10-24)
- Supersedes the world-shear and immediate crater timing described above. Removed the scene split, fracture seam, split uniforms and the scene-alpha visibility mask. The final composite now only combines the scene with bloom.
- The black hole cuts out over ticks 328..330 (0.1 seconds), followed by the existing lens flare at ticks 332..337. A dedicated impact cue arrives at tick 340, showing the four drawings for eight ticks (0.4 seconds). The dense explosion, sound, damage and flying debris begin at tick 348; the burst does not restart the impact drawings.
- Terrain carving starts at tick 352, 0.2 seconds after the visible burst. Reused the center-out crater cursor to resume across ticks, with a six-millisecond target budget checked between columns and a maximum of 2048 columns per batch. A single column may exceed the target. Natural vanilla chunk updates replace the manual immediate flush, and the now-unused chunk-holder access widener was removed. The final crater radius/depth and mob suction are unchanged.
- Validation: 81 JUnit tests passed, including impact/flare state preservation, no impact restart on burst, abrupt core disappearance, server/client phase spacing and resumable crater traversal. Build and access-widener validation passed. Native GPU checks loaded the actual Minecraft five-stage post processor and checked bloom, dense-body opacity/detail, foreground occlusion and odd/tiny/resized targets.
- Pending gameplay: frame pacing with actual chunk rebuilding and network delay in the modpack. The server budget reduces uninterrupted carving work; it is not a measured FPS guarantee or a strict limit on client meshing time.
- Backup: .local-backups/20261023-234101-supernova-sequence.


## Visual contact and impact suite (2026-10-24)
- Spatial Path: removed activation/deactivation particle clouds, continuous foot rings, enchant sparks, per-block white end rods and Path Walker block crumbs. Existing block movement and restoration stay intact. The quiet field now has four rising streams and one broken arc with lower opacity. Projectile contacts and mob repulsion send a surface normal; two feathered ripples indent and spread across the shell, fading over 18 ticks and becoming faint in the first-person forward view. Contact storage is capped at 32 and mob cues are throttled to once per 12 ticks per mob.
- Wand: the floating head opens progressively, the core changes color by skill, charge accents rotate around it, and Supernova compresses/darkens the core. First-person charging draws the wand back, with a damped release recoil. All procedural scales/visibility reset on the next render to prevent state leaking between skill modes and inventory items.
- Drill / Stone Cannon: added client meshes for jagged warm fractures, curved dust fronts and ballistic stone facets. Cannon receives the actual hit-face normal so the impact follows walls as well as floors; the existing short pressure cone still follows projectile travel. Earth Drill uses its own slam cue, avoiding duplicate floor effects from Cannon's camera-shake cue. Surface sampling uses loaded chunks only; nearby raycasts place wall/floor details. Replaced the old impact particle rings.
- Supernova: added a terrain-following dust front with outward moving stone facets, plus a short local rumble/trauma cue when the front reaches a nearby grounded viewer. Terrain heights are captured before the delayed crater changes them. Removed the old repeated smoke/block-particle surge and large vanilla explosion-emitter cloud; the dense shader body, existing large flying blocks and impact-before-terrain timeline remain.
- Bounds: maximum eight concurrent surface bursts, 24 small stone facets per drill/cannon or 64 per Supernova, no new debris entities. Surface samples are captured once, not raycast every frame. No screen-space aura distortion was added.
- Validation: 86 JUnit tests passed, including new contact/surface-normal packet round trips, ripple bounds/front visibility/expiry, floor/wall/nova mesh finiteness and lifecycle, directional fragment motion, wand reset/recoil and complete charged-model exports. Build/access widener checks passed. Native Minecraft position-color shader checks rendered the actual exported meshes, all five charged wand modes, aura inside/outside views and complete foreground-depth occlusion.
- Previews: build/previews/suite-preview-*.png; synthetic backgrounds and surfaces, not modpack gameplay screenshots. Pending gameplay: contact timing with real projectiles/mobs, sampled uneven terrain, dust readability with the full blast, first-person recoil and FPS/chunk rebuild behavior. Other players' held-item charge animation was not validated.
- Backup: .local-backups/20261024-002331-visual-impact-suite.


## Readable Spatial Path and complete Supernova lead-in (2026-10-24)
- Supersedes the low-opacity aura tuning above: six rising ribbons plus two arcs, wider feathering and stronger cores. Third-person visibility no longer fades with the player's facing direction; only the first-person view clears the forward cone. Particle spam stays removed. Native daylight views show a much larger visible area without added black pixels or foreground-wall bleed.
- Added SupernovaSequence as the common choreography for block swallowing, core cutoff, infall meshes, framing and audio. Peeling ends at tick 250. Individual blocks start swallowing at ticks 250..264 by a stable positional seed, finish by 326, then the core cuts at 328..330. Flare starts at 332, impact drawings at 340 and the existing dense burst at 348. The delayed, bounded crater work still begins at 352.
- While holding the wand, custom energy arcs reveal the target seed and footprint. During the black-hole phase, twelve tapered infall trails and three tilted rotating crescents converge on the rim. Trails fade before wrapping their movement cycle and drain into the rim before the cutoff; no extra entities are created.
- Camera framing slowly widens during accretion and tightens during compression, with a subdued tremor that settles for the quiet beat. The black-hole scene gains mild peripheral exposure/desaturation while the source is visible; foreground source occlusion disables that grade. The explosion body itself remains unchanged. Reduced the old repeated aftermath camera kicks.
- Added five original procedural Ogg cues: gravity drone, rising compression, vacuum cut, lens flare and blast. The listener-relative score uses distance weighting and the Players volume category so long-range casts retain audible anticipation. Environment gain falls smoothly into the quiet beat and restores on burst. Removed per-tick server charging shake/sounds and the old native particle markers/jet spray. Lowered redundant burst/aftermath sound layers.
- Audio source/rebuild: tools/generate_supernova_audio.py (NumPy + ffmpeg; these are authoring tools, not mod runtime dependencies).
- Validation: 89 JUnit tests passed, including staggered swallow/cut/flare order, pressure/framing/audio envelopes, readable aura coverage across its cycle and bounded finite cinematic mesh exports. Build and access-widener checks passed. Native Minecraft post-processor load, five-pass shader/bloom/depth/resize checks and dense-body regressions passed. Native combined mesh + post-pipeline previews exercise the lead-in, compression, cutoff, flare and burst; native world-shader aura views verify first-person clearance and foreground occlusion. All five audio files decode through Minecraft OggAudioStream and remain below full scale after Vorbis decoding.
- Previews: build/previews/nova-cinematic-*.png, nova-buildup-*-mesh.json, nova-seed-*-mesh.json and aura-redesign-*.png. These use synthetic backgrounds, not in-game captures, and do not include the server's flying block entities.
- Pending gameplay: complete timing/audio mix in the user's modpack, actual projectile contacts and frame pacing while chunks rebuild. Native shader and audio decoding checks do not establish gameplay FPS or subjective cinematic quality.
- Backup: .local-backups/20261024-005133-nova-cinematic-aura.


## Audio polish, one-shot crater and 1.21.1 crafting repair (2026-10-24)
- Skill selection is silent for all modes and clears an active use before changing modes. Removed Stone Cannon's selection-time Elytra/impact samples. Its wind is now one client sound voice, beginning only beyond 50 use ticks, ramping toward full charge and stopping on release/cancel/mode switch/disconnect; repeated level-up Elytra samples were removed.
- Spatial Path has quieter on/off cues and a six-tick per-player contact-chime limit. Reduced restoration stone/shutter chatter; permanent displacement no longer emits shared block-break audio. Lowered gravity-orb launch/recall layering and removed extra per-victim hurt/stone samples. Earth Drill wind/stone cues play less frequently with lower volumes, a short wind cue precedes impact, and redundant impact layers were removed/reduced.
- Supernova charge uses a vanilla Beacon tone. From activation onward the only spell-audio event is godwand:supernova_sequence, streaming assets/godwand/sounds/supernova_sequence.ogg once at pitch 1. The supplied placeholder is valid silent Vorbis. Removed the five generated score assets and their generator, server startup/burst/aftermath sounds and the client dust-front explosion sample. Gameplay mob/world sounds are ordinary game audio. See docs/SUPERNOVA_AUDIO.md for replacement instructions and timeline.
- Supersedes the 6ms resumable crater batches above: the full existing crater traversal commits once at burst +4 ticks, after the impact drawings, then vanilla publishes merged terrain updates. There is no later crater-carving loop after the explosion. The final crater shape/radius, unbreakable-block handling and loaded-chunk restriction remain. This trades repeated chunk rebuilds for a larger single server operation; frame pacing on a large real world remains unmeasured.
- Burst duration is 280 ticks instead of 240. Dense mass cooling/fade is held back by 40 ticks (two seconds) while raw-age texture flow keeps animating. Expansion, early rays and impact timing retain their original clock. Manager lifetime now covers the full burst.
- Crafting: corrected the ingredient entries from strings to 1.21.1 item objects; retained the existing materials/result. Removed the ignored duplicate plural recipes path. Added a singular advancement/recipes unlock on acquiring a Nether Star.
- Validation: 92 JUnit tests passed, including high-charge wind gating/ramp, no vanilla Supernova charge tone after activation and projectile-volley/respawn chime behavior. Native Minecraft 1.21.1 Recipe and Advancement codecs accepted the fixed assets; the old ingredient schema was explicitly rejected, and correct 3x3 input produced one wand while wrong materials and 2x2 input failed. The isolated native recipe harness uses test-process access fixes and opens the test item registry to register the wand; it is not a full Fabric gameplay session.
- Native GPU checks: extended dense phase keeps moving, the body remains visible at tick 160, and the effect fully expires at tick 280. Five-stage bloom/depth/resize and dense-body background-separation checks passed with the updated expiry baseline. Minecraft OggAudioStream decoded the placeholder and verified silence. Packaged resources were checked for the corrected recipe/unlock, the single current audio slot, exact shader bytes and no stale generated audio assets.
- Pending gameplay: subjective mix for skills 1/2/3/5, actual loop cancellation in the modpack, and the single crater operation / client chunk rebuilding under real load.
- Backup: .local-backups/20261024-081040-audio-nova-recipe-polish.


## Wind comfort, drill impact and temporary-container safety (2026-10-24)
- Stone Cannon: kept two trailing ribbons and the existing delayed fade-in; reduced ribbon alpha from 115..190 to 58..100 and angular speed by 30%. The stone itself and projectile-flight effects are unchanged.
- Earth Drill: maximum-level formation is three blocks higher (35.3 above the target center instead of 32.3); other levels retain their hover heights. The descent continues through the impact cue. Excavation starts one tick after that cue and visits at most 96 center-out columns per tick, using bulk update flags instead of cascading neighbor updates for every cell. Unloaded chunks are skipped. The 34-tick seismic effect covers the full traversal (15 ticks at level 3 and 24 at level 4); damage and crater dimensions are retained.
- Spatial Path: floor detection accepts the 1/8-block height transition between soul sand/path and full dirt, uses the player's collision context, and retains overlapping support surfaces while continuing to open full-height walls.
- Temporary containers: snapshot NBT, detach the block entity before the vanilla replacement callback, then clear the detached inventory and close invalid open menus. Refused block replacement reattaches the untouched original inventory. The saved record is registered before creating a visual display. This avoids relying on SKIP_DROPS to suppress inventory scattering and preserves unopened loot tables.
- Validation: 94 JUnit tests and the complete Gradle build passed. An isolated native Minecraft harness exercised actual replacement callbacks, NBT snapshots/restoration and refusal rollback for chest, trapped chest, barrel, hopper, furnace and shulker box; it also checked double chests and unopened loot tables. A negative control confirms that replacing a still-attached container reaches the unsafe scatter path. Native collision shapes were checked for dirt path, soul sand, farmland, slabs, two-layer snow and carpet.
- Native crater checks: level 3 produced 10,222 edits in 15 ticks (peak 2,843 edits/tick); level 4 produced 21,431 edits in 24 ticks (peak 4,034 edits/tick). Every expected solid cell was visited once and unloaded chunks produced no edits. These use an isolated world test double with actual Minecraft block callbacks, not gameplay or FPS measurements.
- Manual checks still needed: walk/sprint across path-to-dirt, soul sand, slabs/stairs and jump transitions with Bridge both on/off; activate Path/Singularity beside a filled double chest and an open inventory, verify no item entities and exact restored counts; compare level 3/4 impacts and first-person Cannon comfort in the modpack.
- Backup: .local-backups/20261024-083618-wind-drill-support-inventory (includes the previous JAR and native QA harness).


## Supernova sky aperture and pressure fronts (2026-10-24)
- Reference inspected: others/extracted_railgun/assets/orbital_railgun/shaders/post/strike.fsh (sky/depth masking, procedural stars and world-space shock shell). Implemented original GLSL 150 effects for the existing Fabric 1.21.1 pipeline; no reference textures/audio/shader code were copied.
- Sky aperture: an irregular violet tear opens on a world-anchored plane 110 blocks above the black-hole center, with folding luminous edges, drifting corona, nebula color and direction-anchored antialiased stars. Only untouched sky depth is replaced; a vertical-ray fade prevents a dark horizon band. It opens during activation, remains through impact, then closes smoothly during the aftermath.
- The separate sky clock survives the explosion payload without extending the cinematic FOV/vacuum-silence clock. Both clocks clear on disconnect/world reset and expire with the sequence.
- Pressure: three broad feathered gold/white fronts expand with five-tick offsets over the captured terrain; outer fracture strokes appear as the front reaches them. Added 192 procedural ember facets per nova, with drift, fall and a soft 90-tick individual lifetime. The client burst lasts 140 ticks to cover the last embers. No additional server entities or terrain edits were added.
- Fixed Earth Drill surface VFX routing to accept the new 34-tick slam duration as well as the old 22-tick duration, while continuing to exclude Cannon's 12-tick camera-only slam.
- Validation: 95 JUnit tests and build/access-widener checks passed. Tests cover moving pressure geometry, light suppression on unknown terrain, finite/bounded geometry and ember expiry. Native five-pass GPU checks cover sky opening/closing, exact restoration at expiry, foreground occlusion, horizon/ground isolation and unrelated-skill isolation; the actual Minecraft post loader accepts the shader JSON. Native sequence-state checks verify the sky survives detonation while camera/silence stop, plus reset and lifetime expiry. Existing bloom/depth/resize checks and the actual Minecraft position-color mesh render checks passed.
- Previews are synthetic render checks, not gameplay: build/previews/nova-rift-open.png, nova-rift-opening.png, nova-rift-closing.png and suite-preview-2-0.png. Gameplay quality and modpack FPS remain unmeasured.
- Backup: .local-backups/20261024-084957-nova-sky-rift-pressure (previous source/JAR and the native sky QA harnesses).


## Earth Drill / Cannon charging audio (2026-10-24)
- Root cause: Earth Drill formation/spin sounds were emitted at a distant elevated core with sub-1 positional volume; they became inaudible well before the caster's usual target range. Cannon had level-up chimes and late Elytra wind but no continuous early stone-forming layer.
- Earth Drill now starts one listener-relative sequence from its existing activation payload. The default fallback contains stone lift/compression, accelerating grind/air, descent and impact cues, with distance gain out to 160 blocks. Removed the old server sequence sounds to avoid doubling the fallback/custom track. The activation payload reaches listeners within 160 blocks. Shared server constants fix assembly end at 88, spin end at 158, impact at 167 and sequence end at 176 for every charge level.
- Added one valid silent Ogg placeholder, earth_drill_sequence.ogg. Its exact hash selects the audible vanilla fallback; replacing that Ogg selects one streamed custom track at pitch 1 without changing JSON/config. No custom silence is imposed while the placeholder remains. See docs/EARTH_DRILL_AUDIO.md for the release-to-impact timeline and TPS caveat.
- Cannon now has an initial heavy-core placement, gritty early stone hits and bounded grind pulses every 12 charge ticks, with increasing pitch/volume. The existing delayed Elytra voice and its stop/release/mode-switch behavior are unchanged.
- Validation: 97 JUnit tests and build passed. An isolated native Minecraft client harness recorded fallback cues across all four levels, checked the common descent/impact ticks, listener-relative nonzero gain at distant cores, decoded a valid nine-second replacement Ogg, verified exactly one pitch-1 custom event with no vanilla overlay, and verified disconnect stops it. The sound manager is a recording test double; no speakers/headphones or actual gameplay mix were tested.
- Backup: .local-backups/20261024-092107-drill-cannon-audio.


## Supernova charge-to-return choreography (2026-10-24)
- Sky opening now follows the local player's actual seven-second item use, rather than beginning after activation. The target matches the server's block-center target or loaded-column air-aim fallback. Cancelling charge reverses the aperture over roughly one second. The existing charge seed uses that same target. Other viewers still receive the activated sequence; the seven-second preview is local to the charging player.
- Activation keeps the fully open aperture and texture clock continuous. One short camera impulse/FOV kick accompanies a single outward pressure shell from the black-hole center; the pulse ends in 22 ticks and does not repeat during the subsequent buildup.
- The original black-hole compression, flare, impact frame, detonation and terrain timing remain. At detonation the aperture expands from 1.0 to 1.65 over 20 ticks, holds briefly, then closes from sequence tick 402 to 474. An inward world-space pressure shell and closing aperture lip use that same closure curve. No extra damage, terrain restoration or server entities are introduced.
- Shared SupernovaSequence helpers control aperture scale, collapse and pulse radius/strength. Separate shader uniforms keep the sky target independent of the currently rendered explosion center. A sky-only post pass renders the charge preview before any impact is active; it leaves foreground color unchanged and retains depth/horizon masking.
- Validation: 98 JUnit tests and complete build passed. Native five-stage shader checks verify monotonic quarter-charge views, the outward pulse, burst expansion/return, exact sky restoration, foreground/horizon isolation and unrelated-effect isolation. The real Minecraft post loader accepts the updated uniforms. A native client harness with a real wand stack checks use-time-driven preview, cancellation, air-aim fallback, scale continuity on activation, unchanged camera/silence end, expiry and reset. Player/world test doubles are used; these are not gameplay/FPS measurements.
- Synthetic previews: build/previews/nova-charge-35.png, nova-charge-70.png, nova-charge-105.png, nova-charge-140.png, nova-release-pulse.png, nova-return-20.png, nova-return-82.png and nova-return-110.png.
- Backup: .local-backups/20261024-093518-nova-charge-choreography.


## Violet / rose black-hole treatment (2026-10-24)
- Unified the sky tear, activation/return pressure shells, black-hole photon rim, accretion plasma, polar wisps and client infall ribbons around violet with rose-red highlights. Shared GLSL palette constants replace the old orange disk/cyan jet/rainbow horizon rim. The hot explosion body retains its existing treatment.
- The photon rim now hugs the event horizon, with an antialiased narrow bright edge and a softer violet halo. Direct glow is suppressed inside the horizon to retain a dark core. Added two broken 3D turbulent gas shells with depth-tested ray/sphere hits, drifting noise, curl filaments and sparse cloud patches. These shrink/fade with the existing swallow phase.
- Replaced evenly spaced bright disk bands with broken, noise-driven spiral plasma; shortened the polar wisps and removed their rapid brightness modulation. Collapse flare now has a rose-white horizontal streak and a short vertical star.
- Added 24 short inward comet trails and four broken energy arcs to the existing client mesh. No particle/entity spawns, terrain, charge duration, audio or cinematic timing changes. Existing 10,000-vertex/finite-position tests still pass with the additional geometry, and now enforce a consistent violet/rose palette on visible buildup vertices.
- Validation: 98 JUnit tests and full build passed. Native five-pass shader, foreground occlusion, bloom, odd/tiny/resize, actual Minecraft post-loader and combined infall-mesh/cinematic shader checks passed. Sky charge/release/return checks remain green. Synthetic black-hole image assertions confirm a dark center, a distinct brighter rim and substantial violet-magenta aura coverage. Preview: build/previews/nova-black-hole-240.png and nova-black-hole-flare.png. These are synthetic shader views, not gameplay/FPS or reference-image equivalence evidence.
- Backup: .local-backups/20261024-094936-violet-black-hole.


## Sky-rift movement / daylight polish (2026-10-24)
- Charge now locks its target once. The server retains that same terrain target until activation, so walking or turning while holding use no longer drags the aperture across the sky or retargets the black hole. Cancelling, changing skill, starting a new charge and server reset clear/replace the lock. The charge reticle reports the locked target distance.
- Shared target resolution retains block hits and the loaded-column air-aim fallback; client preview does not load missing chunks. Star positions now use the aperture's world-plane coordinates rather than view direction.
- Changed the blue-heavy void/nebula colors to dark red-violet with rose plasma, including the black-hole vignette and infall mesh. Softened the aperture lip and removed the orbit/ground-ring charge seed. The sky opening remains the visual focus during the seven-second charge.
- Limited black-hole refraction to at most 0.012 UV with a smooth cutoff at four core radii, replacing the previous global 0.35 UV pull that repeated terrain into sharp concentric bands.
- Closure starts 40 ticks later (sequence tick 442 instead of 402); its 72-tick inward animation now ends at 514. Detonation, impact-frame, damage, terrain and audio timing remain unchanged.
- Validation: 98 JUnit tests and full build passed. Native five-pass shader checks passed on a blue daytime background, including a red/violet color assertion, camera translation, charge growth, exact sky restoration, foreground/horizon isolation and release/return pulses. Actual Minecraft post-loader and combined cinematic mesh/shader checks passed. A client/server test-double harness checks locked targets through aim changes, recast/cancel/reset and the existing sequence lifecycle. No in-game visual/FPS verification was performed.
- Synthetic previews: build/previews/nova-daylight-charge-full.png, nova-daylight-charge-moved.png and nova-black-hole-240.png.
- Backup: .local-backups/20261024-101515-rift-anchor-color (prior source and JAR).


## Spatial Path movement audio (2026-10-24)
- Root cause: disabling PATH_WALKER break particles also suppressed its basalt movement sound; the restore sound explicitly excluded PATH_WALKER. Toggle sounds were unaffected.
- Successful visible block displacement now plays a quiet basalt separation sound independently of particles. Restoration plays the existing stone-placement cue at the actual restored position. Removed the random coordinate-gated vault-shutter cue for PATH_WALKER so it does not add stray mechanical clicks.
- Opening and return sounds are limited independently per owner (6 / 8 ticks), with runtime-reset and backwards-clock handling. Nearby players receive the same positional sounds. Other skills keep their existing audio and no particles are re-enabled.
- Validation: full test/build passed (99 tests). No direct in-game listening was performed; volume/texture balance still needs player feedback.
- Backup: .local-backups/20261024-102510-path-movement-audio (previous manager source and JAR).


## Supernova sky loss / return (2026-10-24)
- Removed black-hole screen refraction and zoom blur, scene-derived bloom and the cold pressure tint/vignette. They shifted or brightened existing terrain edges and changed the daylight background between the sky-only charging pass and the active black-hole pass. The existing dedicated emission bloom still lights the actual VFX.
- Charging and active sky now share the same tone mapping and red-violet nebula palette. A native pixel comparison checks that the charged sky retains its color on activation.
- Detonation expands a world-oriented angular mask across the whole sky dome over 20 ticks. At full coverage every view direction loses the daytime sky; terrain is dimmed to 14 percent of its incoming color while the explosion remains luminous. This is a client visual effect, with no change to world time, weather, damage or terrain timing.
- The sky holds through sequence tick 442, then sky coverage and terrain darkness recede together over 92 ticks, ending at 534. This adds one second to the prior closing duration. All sky uniforms return to zero at the end.
- Validation: 100 JUnit tests and full build passed. Native five-pass shader tests check charge/activation color consistency, full sky loss in four camera directions, terrain dimming, exact daylight recovery, bloom, depth occlusion and resized targets. The actual Minecraft post-effect loader accepts the new SkyBlackout uniform. No direct gameplay/FPS verification was performed.
- Synthetic previews: build/previews/nova-daylight-activated-color.png, nova-all-sky-blackout.png and nova-return-186.png.
- Backup: .local-backups/20261024-103051-nova-sky-blackout (prior source and JAR).
