# I am Atomic - 1.3.0 experimental

Development lives in `god-wand-atomic` on `feature/i-am-atomic`. The current Magitech wand model and holding transforms were brought forward from the main model work. Both experimental jars target Minecraft 1.21.1. The released 1.2.0 worktree is separate.

## Play

Run `.\gradlew.bat :fabric:runClient` from this repository root. Ordinary launches do not load the optional QA mod. Hold V, choose sector six, then hold Right-Click for **19 seconds**. Early release cancels and immediately frees movement. Full charge commits the final ATOMIC animation while right-click remains held. Keep holding through the arm snap and compression: the scene reaches a dark waiting shot, with subtle arm/staff/camera idle and a sustained low hum. After three seconds a small "Release your button" hint fades in. Release triggers a quick flash, impact and then detonation over eight ticks (0.4 seconds); its cooldown starts on release. Releasing after full charge but before the waiting shot queues the same release after a three-tick minimum dark beat. Changing the held item cancels a preblast cast and clears protection. Mode selection remains blocked while using the item, as for the other modes. Movement, jumping and flight displacement lock until the camera return finishes. The server rejects incoming damage throughout the cast, including the last return frames; it does not change the player's game mode or leave an invulnerability flag behind. Starting while mounted is rejected. Esc/F5 skips only the camera; it does not cancel a committed cast or release its movement lock.

| Tick | Presentation |
| --- | --- |
| 0-40 | Both arms thrust forward; one mild activation shake, pressure rings and shader impact |
| 12 onward | A double source sigil at the caster's feet feeds the violet ground filaments |
| 40-115 | Outward trace POV lags behind the faster branching filaments and rises slowly; no letterbox |
| 115-205 | Straight-down camera descends; roof circuits build from 115 to 180, then their junction wave expands until 203 before the castle cut |
| 205-285 | Fast diagonal sweep past the fortress: 52-block-wide inner walls, eight outer watchtowers reaching 50 blocks high |
| 285-370 | Slightly elevated close view; arms rise from 294 to 344 while I / I AM appears, one horizontal halo forms and eight energy ribbons converge overhead |
| 370-396 | Hero camera lowers and aligns squarely with the caster while remaining close |
| 382-396 | Raised arms sweep inward across the torso to prepare the release; the rendered head and body face the cast direction |
| 390-396 | Random energy motes briefly stop rising before the snap |
| 396-402 | ATOMIC, letterbox, impact, arm snap, camera push and immediate fortress compression start together; release particles and one circular shockwave burst outward |
| 402-406 | Inward rings consume all remaining energy at the feet; the shot darkens |
| 406 (held) | Scene waits for release; subtle idle uses real time and the hint appears after three seconds |
| 406-410 after release | Fast violet-white flash lifts the dark shot |
| 410-412 after release | Brief shader impact frame, then the existing detonation cue at414 |
| 414-466 | Blackbars disappear at detonation; a full-screen low view looks up the growing column |
| 466-614 | One distant shot adapts to loaded terrain; passing pressure fronts add separate short shakes, led by two ticks |
| 614-734 | One close angle follows the column narrowing; height stays 1,024, with no late global flicker |
| 734-774 | High oblique view reveals the crater and full 98-block-radius violet circuit imprint |
| 774-826 | Return behind the shoulder, blend into normal perspective, then unlock movement |
| 834-1434 | Full-area imprint stays visible for 30 seconds after the cutscene |
| 1434-1494 | Imprint fades smoothly over three seconds, then is removed |

Three buildup cuts (115, 205 and 285) have a soft violet fade over six ticks on either side. The activation-to-trace cut at tick 40 stays direct. This is a GUI transition, available with post effects off; it adds no transition or impact frame to the explosion shots. Every three seconds during the established buildup, one circular electric wave expands from the caster to the fortress. A bright circumferential band then climbs the wall faces, crenellations and square tower faces, tapering along each pointed roof to its tip. It follows the actual architectural outlines rather than sending separate packets along individual routes.

There are no floating terrain blocks or spherical blast meshes. Buildup never edits terrain. The scene contains41.7 seconds at20 TPS plus the held dark pause (at least0.15 seconds for an early buffered release). Camera paths check loaded chunks and solid terrain, limiting horizontal distance to the current render distance rather than loading more terrain. The distant shot lowers its nominal height when the loaded radius is short and frames the entire blast footprint using the actual camera position. Before the shot begins it chooses a clear azimuth from a few candidates, samples the full planned drift over the terrain and holds the chosen direction/height for the shot. It checks endpoint clearance instead of pulling the distant camera back along a collision ray from the caster. Terrain lift is capped at 60 blocks to preserve a wide horizontal composition; fully enclosed terrain can still obscure the base. The current default-distance check uses eight chunks. Other shots retain the collision-safe camera. Player rotation and perspective preferences are not rewritten.

Observers receive geometry, distance-scaled sound, shake and shader effects while keeping their own camera and movement. Horizontal damage grows with the column, up to radius 98 and height 1,024, falling off from 240 at the center. Each living entity is struck at most once. Caster and creative/spectator players are excluded. Normal Minecraft damage handling applies. The default cooldown is 30 seconds, scaled by the existing server setting.

## Terrain and settings

Outside the 98-block column, the visible outgoing ground pressure rings can strike mobs up to 130 blocks away for15 health points per hit by default. Swept front tests follow the same radius function used by the renderer and ignore cycle wraparound. A ten-tick per-target cooldown prevents repeated damage while one front overlaps a mob. This secondary damage affects mobs near the ground wave plane (caster Y -2 to +3), excludes players, and does not carve terrain or extend the primary column damage.

Crater carving is permanent. The default/max radius is 98; smaller values in existing configs are respected. Carving follows the expanding column, clears eligible blocks up to its full height, and cuts a bowl at most 14 blocks deep. Work may lag the visual front in tall terrain because all Atomic tasks in a world share an 8,192-inspection budget per tick. Containers, fluids, unbreakable blocks, positions held by active spatial spells, unloaded chunks and the three-block caster pedestal are preserved. Pending work can outlive the effect; stopping the server interrupts it.

Aftermath is a temporary violet decal over the full blast footprint (98-block radius), including protected bedrock. It remains visible for 30 seconds after the cutscene ends, then fades over three seconds. A new Atomic cast by the same caster clears their previous imprint. Client geometry follows the loaded terrain heightmap, including uneven surfaces; it remains available with post effects disabled. A bounded client cache retains at most 64 imprints and draws the nearest eight within 256 blocks. It is cleared on world changes and is not saved to disk. No replacement blocks are created. The crater itself remains permanently carved. Existing crying-obsidian blocks left by an earlier experimental build are not restored automatically.

- `config/godwand-client.json`: `atomicCutscene: false` keeps the normal camera and HUD. Movement still locks during the spell.
- `config/godwand-server.json`: `atomicTerrainRadius: 0` disables carving; allowed range0-98. Visual and damage range are unchanged. `atomicDamage` sets maximum primary damage at the column center (default9999, linear falloff to zero at98 blocks). `atomicShockwaveDamage` sets each successful outgoing pressure hit (default15). Both accept finite values0-1,000,000; zero disables that damage source and its knockback. Existing partial configs use defaults for missing keys and retain their original file contents. Add the keys explicitly to customize, then restart the game/server:

```json
{
  "atomicDamage": 9999,
  "atomicShockwaveDamage": 15
}
```

Merge these keys into the existing server JSON, preserving its other settings. Armor and damage immunity still use Minecraft's normal damage handling.
- Existing `postEffects`, `impactFrames`, `cameraFov`, `cameraShake`, `skyBlackout`, `bloomStrength` and `bloomDownsample` settings apply. Shader filters require `postEffects` and `impactFrames`; geometry remains when post effects are off.

At most four Atomic tasks are active per world. Clients retain at most eight active meshes and one full volumetric Atomic per view, prioritizing their own cast. The Atomic post chain is independent of Supernova.

## Assets

Energy meshes, volumetric dust and shader code are procedural; no vanilla smoke particles are used. Far-side dust stops at the front surface of the light column; the geometry fallback also rejects occluded dust ribbons. The fortress replaces the curved birdcage ribs with eight square watchtowers, pointed spires, crenellated curtain walls, pointed-arch windows and angular overhead circuits. It has1,536 small round violet motes with soft billboard glow, plus96 fine ascending streaks and 20 gas ribbons. A 256-by-128 procedural alpha atlas provides circular sprites, a sparse circuit tile and a white region for plain geometry in the existing shared batch. Forty-eight circuit panels overlay the eight curtain walls and 32 tower faces with fine vertical traces, diagonal junctions and small luminous nodes; no additional particle entities or dependencies are needed. Motes spawn at random points on the ground and rise vertically with varied lifetimes and heights. They fade before recycling at a newly randomized point; selected emitters leave small ground ripples. Eight curved ribbons carry bright packets into a single focus above the caster's head. At ATOMIC, that focus releases one circular ground shockwave centered on the caster, with one mild decaying camera impulse. During the distant blast shot, each pressure ring adds a brief shake just before its actual rendered radius passes the camera; the gaps between rings remain quiet and no extra impact filters are added. Motes pause at tick 390, burst radially outward by up to 12 blocks during the arm snap, then join the collapse. Fortress, outgoing release rings and motes have separate transforms so the early outward punch remains readable while the fortress already compresses inward; all are consumed at the caster feet by scene tick406 before the held pause. Ground circuits scale around ground level instead of translating upward toward the chest; the shader volume uses the same ground origin. Fewer redundant mote tails retain the geometry budget while increasing the visible field. A shader pressure front also rolls outward through the gas. There is no forward cone or directed ring stack. The release is visual; radial damage still begins with the column detonation. The player pose uses the existing vanilla rig: a torso turn, normal straight legs, inward arm windup, fast outward arm snap and wand cant, with frame interpolation and a blend back during the shoulder return. During buildup, the head makes smooth seeded random glances with a slightly raised gaze, rather than following a downward mouse pitch. It blends to a forward head at the ATOMIC windup and restores the normal view after the cast. Blackbars exist only from ATOMIC until detonation, then the blast and aftermath use the full screen. The facing mixin adjusts only the rendered root transform; it does not rewrite the player or server view. No animation dependency is required. The supplied I AM, ATOMIC and oblique-column reference stills guide silhouette, framing, pacing and color; they are not shipped as textures. `tools/generate_atomic_audio.py` synthesizes the original charge/blast sounds using numpy and ffmpeg. No anime voice recording is included. The held state loops the existing Minecraft beacon ambient sound quietly in the player sound category; it stops at release or cancellation. Bebas Neue is shipped unchanged with its SIL OFL license beside the font.

## Verify

```powershell
.\gradlew.bat :common:test :fabric:build :neoforge:build --offline --console=plain
```

Use `fabric/build/libs/godwand-fabric-1.3.0.jar` or `neoforge/build/libs/godwand-neoforge-1.3.0.jar`. The root legacy jar and QA probe are not release artifacts.

Manual checks: use a separate expendable world; cancel early and move again; cast fully; verify the two shader impacts, branching trace POV/source sigil, downward overhead motion, smooth arm poses and dolly, three principal column shots and shoulder return. Check that the full-area imprint survives the cutscene for 30 seconds and then fades over three seconds, or clears when the same caster begins another cast. In Survival, check mob/fire damage during the cast and ordinary damage after it ends. Try camera skip and post effects off. Check observers, multiplayer, dimension changes and shaderpacks separately before release.

### Optional gameplay probe

`tools/atomic-qa/AtomicGameplayProbe.java` is an unshipped Fabric test mod. It runs only in the explicitly selected disposable test world. It changes inventory/game mode, prepares a 201-by-201 grass/dirt surface and casts through the real item/network/server path. It captures selected native screenshots, asserts GPU shader compilation, client/server movement lock (including a forged movement packet), no lifted blocks, actual terrain destruction to radius 94, pedestal preservation, camera return, early cancellation and Escape handling. Its fallback test temporarily disables post effects and lowers render distance, then restores both. It also asserts the actual rendered arm/torso/leg poses, partial-tick interpolation, a survival-mode zombie attack and fire damage blocked during the cast, ordinary survival damage restored afterward, camera stability inside the loaded chunk margin, and the full-area imprint after camera restoration. The scar check uses post effects off and a stepped test surface, captures it near the end of its 30-second hold, then checks fade and cache removal.

```powershell
.\gradlew.bat -I tools/atomic-qa.gradle :fabric:writeAtomicQaClasspath --offline
$cp = Get-Content build/atomic-qa-classpath.txt -Raw
New-Item -ItemType Directory -Force build/atomic-qa-classes | Out-Null
javac -proc:none -encoding UTF-8 -cp $cp -d build/atomic-qa-classes tools/atomic-qa/AtomicGameplayProbe.java
jar --create --file build/atomic-gameplay-probe.jar -C build/atomic-qa-classes . -C tools/atomic-qa-resources .
.\gradlew.bat -I tools/atomic-qa.gradle :fabric:runClient --offline --console=plain
```

Create the disposable `Atomic QA` flat world once before running the probe. The init script loads the probe only for that invocation using `fabric.addMods` and selects its world with Quick Play. Nothing is installed in `fabric/run/mods`. Captures use `fabric/run/screenshots/atomic-v29-*`; inspect `build/atomic-v29-gameplay.log` for `ATOMIC_QA`. The probe records selected phase screenshots only; it no longer captures every tick for video, as requested.

For an explicitly created alternative disposable world, pass `-PatomicQaWorld="Atomic Regression"` to the QA run command. Production shader routing has no world-name condition.

Geometry uses the same violet line cores from activation onward. The post grade now preserves channel identity instead of progressively replacing red with blue. During the overhead shot, roof circuits use wider, brighter cores and the volumetric gas density is reduced by 80 percent, so the building paths remain distinct under shaderpack bloom. The roof completes at tick 180, with its circular junction wave visible through 203 before the 205 cut.

### V27 historical verification (2026-10-26)

Current clock: nineteen-second charge / 380 ticks, ATOMIC at 396, detonation at 414, camera completion at 834 / 41.7 seconds. Other camera cuts, blackbars, damage protection and the 30-second scar hold remain unchanged. Roof construction alone is retimed to 115-180 with its junction wave at 180-203.

`build/atomic-v27-build.log`: 165 common tests plus two tests per loader, 169 total, all pass; Fabric and NeoForge jars built. The new regressions first failed against floating ground circuits, dim roof cores and roof timing. Swept pressure-front tests cover outgoing crossings, cycle wraparound, unreachable distances and the primary-radius boundary. Meshes retain the 60,000-vertex cap.

`build/atomic-v27-shader-baseline.log` reproduced the washed-out early core and faint roof in a real client using Iris 1.8.8, Sodium 0.6.13 and Complementary Unbound r5.9.3. `build/atomic-v27-shader-gameplay.log` completes the full item/network/server cast with the shaderpack confirmed active. Native captures show distinct roof paths, closure/wave before the camera cut, and the compressed ground circuits under the caster feet. Shader captures are retained under `build/atomic-v27-iris-test/screenshots`.

`build/atomic-v27-vanilla-gameplay.log` passes the same probe with vanilla rendering, followed by a post-effects-off ridge/aftermath test. Both runs verify actual pressure damage: 100-health mobs at 112/128 blocks lose health, while 136-block and airborne controls retain 100 health. Camera/movement/damage restoration, shader uniforms, player poses and crater/pedestal checks pass in both runs. The Iris run ended after its ridge check; the vanilla run continued through the full scar hold/fade/cleanup and restored test settings. Temporary Iris/Sodium jars were moved out of `fabric/run/mods`; ordinary runClient remains vanilla and does not autoload the QA probe. No video was exported.

Other shaderpacks, NeoForge gameplay, multiplayer and the user's full modpack remain untested. Shaderpack exposure/bloom settings can still change apparent brightness. No installed SKLauncher mod was replaced; final jar hashes are recorded in `docs/HANDOFF.md`.


### V28 wall material and control proposal (2026-10-26)

Wall/tower circuit material reuses the existing additive texture batch; 48 extra quads add 192 vertices, with the sampled release peak at 59,340, below the 60,000 cap. The atlas keeps transparent gutters between its circular sprite, circuit and white regions. Unit checks cover sparse texture coverage, gutters and panel UV coverage. A live GPU probe checks all three uploaded atlas regions.

`build/atomic-v28-build.log`:166 common +2 Fabric +2 NeoForge =170 tests pass, both jars built. `build/atomic-v28-shader-gameplay.log` completes all gameplay, persistent scar and fallback checks with Iris 1.8.8/Sodium 0.6.13/Complementary Unbound r5.9.3; the first cast confirms the shaderpack is active and its circuit atlas uploaded. Native castle/hero screenshots inspected at215/245/344. Shader captures retained in build/atomic-v28-iris-test/screenshots. Temporary shader mods/configs moved out of normal dev runtime after the run; user launcher unchanged. No video exported. Other shaderpacks, full modpack, multiplayer and NeoForge gameplay remain untested.

The user is considering holding right-click through ATOMIC, waiting in the darkened shot until release triggers the blast, with subtle idle motion and a small release hint after three seconds. This is a proposal only, NOT implemented: current cast still commits automatically at380 and detonates at414. Recommended presentation: retain a faint readable silhouette, low sustained rumble and mild idle motion instead of a frozen fully black screen. If adopted later, synchronize a server-owned waiting/release state, shift all post-release cues together, use real wait time for idle/hint and handle release during the final cue plus disconnect/item changes. Do not simply freeze the client camera while the server continues its current clock.


### V29 interactive release and final polish (2026-10-26)

This supersedes the V28 consultative control proposal. User approved a trial; pre-change source plus both V28 playable jars are saved outside the checkout. The exact backup path and verification are recorded in docs/HANDOFF.md.

Scene clock: charge380, ATOMIC396, compression complete/WAIT406, release flash406-410, release impact410-412, detonation414, end834. The server and clients freeze scene age406 while the real world clock and held-item input continue. Payload keeps a stable cast-start identity plus an independent releasedAt timestamp; damage, terrain, camera, shader, blast audio and scar lifetime all use the resumed scene clock. Idle motion and the3-second hint use actual held time. No automatic timeout/detonation is imposed while holding. A disconnected/dead/dimension-changed caster or item/skill loss cancels before the blast. Cooldown is spent at committed release. No other skill input branch or1.20.1 source is changed.

The overhead halo is one flat ring at3.2 blocks; tilted orbit rings, stacked rings and extra halo markers are removed. The release keeps one head ring plus its existing circular ground shockwave. Emitters increase512 to1,024, keep random vertical rise and small round sprites, and start more densely during buildup. Two glow layers per mote and fewer redundant trails/ground ripples reduce the sampled398 mesh to52,304 vertices (prior59,340), below60,000.

Validation results are recorded after the final native runs in docs/HANDOFF.md. Historical v27/v28 results above refer to the earlier automatic-release builds, not to this input change.


Final V29 evidence: build/atomic-v29-build.log succeeds with174 tests (170 common and two per loader); both loader jars built. Native vanilla and Iris/Complementary runs both complete all interactive-release, scar and fallback checks and restore settings. The probe holds five seconds, checks actual arm/camera idle plus server protection/no premature carving, releases through the real item callback, verifies buffered release, item-loss cleanup and Supernova charge/cancel afterward. Shared mode selection while using remains rejected. See docs/HANDOFF.md for backup, jar hashes and historical QA failures. Other five skills have not all been fully cast; live external multiplayer, other packs, NeoForge gameplay and1.20.1 port remain untested.


### V30 balance and visual polish (2026-10-26)

User requested stronger configurable primary/pressure damage, more random rising particles, radial source-ring appearance and a forward/inward crossed step. After seeing the step in game, the user explicitly rejected the leg pose and requested normal straight legs. Final source removes all Atomic leg fields, rotations and offsets; vanilla leg positioning remains untouched.

Damage defaults:9999 center,4999.5 at49 blocks, zero at98; pressure15 per successful hit outside the primary radius, same swept fronts and ten-tick cooldown as before. Server settings validate finite0-1,000,000 values; partial JSON keeps missing-field defaults. Native QA uses1000-HP primary targets and15-HP pressure increments. No bypass of armor/immunity or forced entity deletion.

Emitters1024 ->1536, slightly brighter, still small circular sprites at random positions rising vertically. Trail stride12 and ground-ripple stride48 keep those counts stable. The source rings and their24 ornaments scale out radially from the caster over ticks8-36; outgoing paths remain connected to their growing source. Sampled398 mesh57,528 vertices, below60,000. Latest test/runtime results are recorded in docs/HANDOFF.md; the earlier V30 vanilla screenshots include the rejected step, while final Iris screenshots show normal legs.


Final V30 validation:177 tests pass and both loader jars built. The full vanilla probe passes for damage/density/radial source/interactive-release cleanup; it predates the user-requested removal of the crossed step. The final single full Iris/Complementary cast confirms normal vanilla legs, correct9999-center falloff and15HP pressure hits, held release, GPU atlas/shader, camera restoration and settings restoration. Local1280x720/8chunk buildup samples: vanilla min159/median165FPS; Iris/Complementary min62/median73FPS. These are current local observations, not a comparison against V29 or a full-modpack performance guarantee. Native verification and hashes in docs/HANDOFF.md. The QA-only -PatomicQaSingleCast flag stops after one full cast; omit it to run the complete scar/fallback suite.


### Authored SFX (2026-10-27)

Visuals are approved by the user. `sfx/atomic-first.ogg` (23.856s, mono Vorbis/48kHz) ships as `godwand:atomic_charge`; it starts on CHARGE and runs to completion across ARMED, hold and release. `sfx/atomic-last.ogg` (22.056s, same format) ships as `godwand:atomic_blast`; it starts once on RELEASE, with BURST only as a fallback for observers missing the release. Both are copied unchanged into all four builds and stream without looping. Cancellation/world reset stops both; natural cinematic completion lets their audio tails finish without extending the camera.

Hold retains Minecraft warden heartbeats every 28 ticks at pitch0.8; gain increases0.22 to0.30. Temporary beacon ambience and the four placeholder sound cues are removed so they do not overlay the authored mix. `tools/generate_atomic_audio.py` now writes legacy previews to build/legacy-atomic-audio, never to release assets.

Verification: releaseJars --offline succeeded for all four loaders/versions; common JUnit202 tests, zero failures/errors, plus loader/port checks passed. Every installable JAR was inspected for exact authored audio bytes, streaming declarations, the existing shaped recipe in the appropriate version-specific data directory, recipe-book unlock and MIT LICENSE. Native audio playback/crafting is left for user testing. Logs: build/atomic-sfx-release.log; checksums: build/release/SHA256SUMS.txt.
