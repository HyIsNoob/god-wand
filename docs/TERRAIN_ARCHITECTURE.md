# Atomic terrain scheduling

## Reference inspected

The owner supplied `Test/the-shooting-star-demo-1.4.4-forge.jar`. Its metadata explicitly describes separate Minecraft 1.20.1 and 1.21.1 builds in one JAR, selected by a version entry point. Read-only class/bytecode inspection found ExecutorService/CompletableFuture, fastutil primitive maps and direct LevelChunkSection.setBlockState calls. This confirms those mechanisms exist, not a measured promise of millions of blocks without lag. No proprietary implementation was copied into God Wand.

## God Wand implementation

`common/src/main/java/net/godwand/skill/AtomicTerrainPlanner.java` plans the center-out footprint on one daemon worker. The queue is bounded to eight requests; saturation defers a request rather than running it on the server thread. Only a bounded radius crosses into the worker. Primitive int arrays store signed packed column offsets and squared distances. No Level, chunk, block entity or player reference is captured. Coordinate plans contain each eligible column once and exclude the caster's protected pedestal.

`AtomicCrater` resumes on the server thread, uses getChunkNow (no forced chunk loads), and shares the loaded chunk reference across each column in the current slice. It reacquires a chunk after yielding so unloaded chunks are skipped. Every write still checks containers, unbreakable blocks and spatial-displacement ownership, then uses normal world.setBlock flags for client/shape/drop handling. Water, flowing water, lava and other removable fluids use the same route. Minecraft keeps responsibility for chunk dirty state, heightmaps, lighting and block-change packet aggregation.

`AtomicManager` shares an 8192-inspection ceiling and approximately 2 ms of terrain work per world tick across active casts. Time spent in entity damage and cinematic bookkeeping does not consume that terrain allowance. The deadline is checked every 32 inspections; a single expensive modded block callback can overrun this soft limit. Clearing runs only while the energy column is visible. When the column closes, unfinished terrain is discarded; the cast is removed at the cinematic end regardless of terrain progress. There is no catch-up flush or post-cutscene terrain queue. Dense terrain, expensive block callbacks, concurrent casts and unloaded chunks can leave part of the requested footprint untouched.

Terrain reach is 1.5 times the existing atomicTerrainRadius config (98 -> 147 blocks). The requested clearing extends 30 blocks down across that footprint instead of tapering to zero depth at the edge. The energy column, damage radius, aura and camera remain unchanged.

## What this does not claim

Footprint planning is only a small part of the cost. Actual writes, light propagation, chunk mesh rebuilds and other mods' callbacks can dominate. Direct section replacement might be faster, but bypassing normal world updates would require separate verified implementations for lighting, block entities, heightmaps, persistence, hooks and packets on every Minecraft version. That path was deliberately not introduced.

Tests cover full footprint membership/deduplication, bounded coordinates, zero/expired work slices, resumption, loaded-chunk-only access, shared chunk lookups, protected blocks, water/lava removal and the visible-column/cinematic lifetime cutoff. Supernova retains its 60 explosion slices and uses loaded chunks directly without forcing chunk loads. Build/unit tests are not an FPS/TPS benchmark. No native game automation or gameplay performance measurement was run.
