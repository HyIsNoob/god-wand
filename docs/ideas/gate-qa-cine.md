# Silent Gate cinematography QA (Fabric 1.21.1)

Runs: build/cine1..cine4.log (cine4 final; world restored from gate-qa-pristine each time). Sheets: build/fuga-qa/gate-sheets-cine/
(build-0-480.png step 4, barrage.png and ending.png step 3 frames = 6 ticks, key-b<age>.png full-size build frames, key-f<n>.png barrage frames).
Commits: b729d22 (360 tick pass), 2ccb6c1 (480 tick pass, ending, barrage), then the circle-legibility commit.

## Timeline (ticks from click 1; BUILD 480)
| Shot | Ticks | Curve | Content |
|---|---|---|---|
| S1 | 0-40 | linear drift | first person, wand, mana |
| S2 | 40-80 | linear | wand spin, sideways drift |
| S3 | 80-120 | ease-out long | push in, "shh" |
| S4 | 120-170 | linear orbit 0.57 deg/tick | great circle writes 120-168, 2 tick hold |
| S5 | 170-210 | linear | slit opens, camera rises along its edge |
| S6 | 210-250 | static camera | gate emerges 210, falls to 248; gaze lifts 235-250 |
| S7 | 250-290 | spring recoil | touchdown 248, settle to 266, shockwave 274, extreme wide |
| S8 | 290-380 | linear orbit 0.51 deg/tick | stuck 292-316, push apart 316-366, heavy stop to 392; ends frontal |
| S9 | 380-430 | ease-in | camera darts out of the doorway along the circle lane |
| S10 | 430-480 | ease-out long | matched speed, extreme wide |
| WAIT | 480+ | breathe | medium in front of the caster |

Barrage (authored for 200 ticks, scaled): B1 12-48 low rain, B2 48-84 dive from a sky circle, B3 84-134 kill close-up (volley now at tick 100), B4 134-170 ground tracking, B5 170-212 high wide.
Ending (160 ticks after the barrage): E1 0-40 doors close (medium), E2 40-80 wide while circles retract (20-85), E3 80-125 low looking up as the gate climbs (75-125) into the great circle, E4 125-160 wide while the circle unwrites tier by tier (108-160); screen dims 135-160; then 15 tick return.

## Numbers (1280x720, sampled; screenshots every 4th tick in BUILD so FPS is slightly pessimistic)
cine4: FPS BUILD avg 55.6 (min 12), WAIT 59.6 (59), BARRAGE 45.8 (min 7, one hitch with a 73 ms server tick), END 58.5 (56).
cine3 (same code, no face-ring change): BARRAGE avg 54.1 min 30, END 59.5. Quads/tick: BUILD avg 1540 max 2624, WAIT 2453, BARRAGE avg 4041 max 4859, END avg 1908 max 4090 (budget 20000).
killed 14/14, blocks removed ~11400 (about 3.3x the 48-circle run: more circles = more craters; server MSPT max 37-73 ms in BARRAGE).

## Honest shot notes
- S1-S3: unchanged framing, slower; fine.
- S4: circle writes; frame is mostly dark sky with a small circle at first, readable by 150.
- S5/S6: slit and falling gate read well from the static low camera (gate slides down the frame).
- S7: gate, circle and shockwave read; the caster is NOT visible (ground only), so "tiny caster" is not shown.
- S8: very slow orbit; the first half is mostly door leaves filling the frame, the light leaks as thin rays at the edges, the crack opens late in the shot; the end is frontal and bright.
- S9: the camera flies out among circles but the field looks sparse in this shot; circles are large ghostly discs against red sky.
- S10: gate + great circle + about 40 visible face-on small circles; caster off-screen at the bottom. Circles around the gate are not "hundreds" yet in view; those behind the camera are never seen.
- WAIT: caster pointing up with the gold/green ring behind; sky above has only ~6 circles in view (camera faces away from the gate).
- Ending: doors close, circles shrink toward the gate, gate climbs into the circle, circle unwrites ring by ring, sky clears. Reads clearly; the dim to black is dark.
- Barrage: B2 dive ends on empty ground (weak, no tracked target); B3/B4/B5 fine; B1 shows the ground ring.

## Concerns
Small circles at the gate silhouette partly remain (lane circles run along the gate axis); circle count in view in S10/WAIT is lower than the owner expects; blocks removed grows with circle count (terrain damage cost).
