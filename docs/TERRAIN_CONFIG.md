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

Defaults remain true to retain the existing destructive spell style. Set lift=false and terrainDamage=false for pure entity damage with no skill 3/4 terrain movement. Set lift=true and terrainDamage=false for reversible block effects. Set both true for the original destructive behavior. Lift=false with damage=true still allows the explosion/crater to destroy blocks.

Atomic now excavates a bowl up to 28 blocks below its cast ground level, tapering toward its outer radius. Setting atomicTerrainDamage=false still prevents all of its block removal. Existing container, fluid, pedestal and unbreakable-block safeguards remain.

Presets: config-presets/preserve-terrain.json disables terrain movement for 3/4 and permanent destruction for 3/4/5/6. config-presets/reversible-block-effects.json retains temporary lift for 3/4 while disabling permanent destruction for 3/4/5/6. Merge these keys rather than replacing custom damage/cooldown settings.

Reversible lift temporarily removes real blocks and restores original states/NBT using the existing persisted spatial journal, including paired doors/plants/beds. For a build area where even temporary block movement is unwanted, disable the lift options too. Other mods' claims/protection systems have not been tested with this change.

## Invalid config and restoration conflicts

An unreadable or malformed godwand-server.json now stops startup instead of enabling destructive defaults. The existing file is preserved; correct it before restarting. Missing files are still created with the documented defaults.

If a temporary spell's original position contains a newly placed conflicting block or container, the mod retains its saved original rather than overwriting that position. Clear the obstructing position to allow recovery. Existing bridge destinations are retained until the source can be reconciled.
