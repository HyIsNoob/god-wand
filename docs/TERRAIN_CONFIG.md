# Terrain configuration

Merge the selected keys below into config/godwand-server.json, then restart the world/server. K is the client graphics menu; server terrain settings are configured in the JSON file. These values are controlled by the server in multiplayer.

| Skill | Setting | Effect when false |
|---|---|---|
| 1/2 | Existing spatial restoration | Keep their existing temporary displacement/restoration behavior |
| 3 Earth Drill | earthDrillBlockLift | No blocks gathered from the world |
| 3 Earth Drill | earthDrillTerrainDamage | No permanent drill crater/lava or placing flung blocks; lifted originals are restored |
| 4 Supernova | supernovaBlockLift | No terrain peeled into the core |
| 4 Supernova | supernovaTerrainDamage | Explosion does not carve a crater; any peeled originals are restored at detonation |
| 5 Stone Cannon | stoneCannonTerrainDamage | Projectile impact does not excavate blocks |
| 6 Atomic | atomicTerrainDamage | No block removal; damage and cinematic remain |
| 7 Fuga | fugaTerrainDamage | No crater, rock spires or fire placement; damage and cinematic remain. fugaTerrainRadius (default 48, max 64) sets the crater radius |

Defaults remain true to retain the existing destructive spell style. Set lift=false and terrainDamage=false for pure entity damage with no skill 3/4 terrain movement. Set lift=true and terrainDamage=false for reversible block effects. Set both true for the original destructive behavior. Lift=false with damage=true still allows the explosion/crater to destroy blocks.

In 1.3.5, Atomic targets terrain down to 16 blocks below the cast origin, leveling higher terrain before deepening lower ground. Clearing stops when the light column begins retracting; expensive blocks or unloaded chunks may leave depth unfinished. The visible beam still extends 30 blocks below the origin. Setting atomicTerrainDamage=false still prevents all of its block removal. Atomic 1.3.2 clears all removable fluids, including source/flowing water and lava, underwater plants and waterlogged blocks. In 1.3.3, atomicTerrainRadius is a direct maximum radius (default 98 blocks), capped by the visible expanding column. Columns become eligible in circular distance order as the beam expands; removal then prioritizes higher terrain. Existing config values, visuals and entity damage are unchanged. Containers and other removable block entities are destroyed. The caster pedestal, unbreakable blocks and active spatial spell positions remain protected. Unloaded chunks are skipped. Fluids outside the crater can still flow back into it naturally.

Presets: config-presets/preserve-terrain.json disables terrain movement for 3/4 and permanent destruction for 3/4/5/6. config-presets/reversible-block-effects.json retains temporary lift for 3/4 while disabling permanent destruction for 3/4/5/6. Merge these keys rather than replacing custom damage/cooldown settings.

Reversible lift temporarily removes real blocks and restores original states/NBT using the existing persisted spatial journal, including paired doors/plants/beds. For a build area where even temporary block movement is unwanted, disable the lift options too. Other mods' claims/protection systems have not been tested with this change.

## Config upgrades

On startup, valid older client/server JSON files gain any missing options automatically. Existing values and unknown keys are preserved; complete files are left untouched. Upgrades write a temporary file before replacing the original. Newly introduced options use their documented defaults, including terrain destruction enabled by default.

## Invalid config and restoration conflicts

An unreadable or malformed godwand-server.json now stops startup instead of enabling destructive defaults. The existing file is preserved; correct it before restarting. Missing files are still created with the documented defaults.

If a temporary spell's original position contains a newly placed conflicting block or container, the mod retains its saved original rather than overwriting that position. Clear the obstructing position to allow recovery. Existing bridge destinations are retained until the source can be reconciled.

Terrain planning and bounded server slices are described in [the terrain architecture](TERRAIN_ARCHITECTURE.md). Atomic stops clearing before its column starts shrinking, even if some terrain remains. Large or expensive casts can leave a smaller crater so terrain removal never continues after the cutscene; visual density is unchanged.
