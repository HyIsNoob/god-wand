# Boss damage compatibility — 1.3.3

The shared damage path applies to all eight supported targets, including Fabric/NeoForge 26.3 and 1.21.11. The new 1.21.11 targets pass automated checks; boss-mod gameplay testing remains pending.

## Changes

Singularity, Earth Drill, Supernova, Stone Cannon and Atomic use the same server-side spell damage route for non-player living targets. Spatial Path has no damage stage and remains unchanged. Configured damage, charge-level scaling, global multipliers and existing attack timing remain in place; zero damage does not trigger boss-hit callbacks.

The damage type bypasses armor, resistance/enchantment reductions and invulnerability gates used by many modded bosses. It does not bypass vanilla hit cooldowns. Player targets retain the original damage source and protection path. No generic entity deletion or forced-health override was added.

Cataclysm's documented boss hurt code otherwise caps damage and rejects or weakens distant attacks. Its bypass-invulnerability path accepts this source; the optional regeneration/hurt-time tags are included. These are tag-based integrations, not a required Cataclysm dependency. The main blast and Atomic pressure rings both use the shared damage path.

Ender Dragon damage goes through its head-part API. Atomic records a target only after its damage call succeeds, allowing a rejected hit to be retried while the column still reaches it.

Desert Behemoths' worm segments are Entity subclasses rather than LivingEntity. Area-damage stages now also find these parts, resolve the controller and avoid damaging the same worm multiple times through different parts in a single area hit. Optional public blastHit(int) and legacy blastHit() APIs preserve the mod's own retreat/drop logic without requiring its JAR at startup. In Sandworm 1.1.1, the API counts blasts instead of consuming ordinary HP: a 9999-damage spell does not mean 9999 points can be subtracted from that counter, and its vulnerable/retreat phases remain in control.

## Evidence and limits

- Inspected the installed Cataclysm 1.21.1-3.33 JAR and published Cataclysm boss source.
- Inspected installed Sandworm 1.1.1 public methods and published controller/segment source.
- Tests cover configured damage/attacker attribution, bypass tags, player-source preservation, rejected Atomic hits, dragon part routing, both optional worm APIs, per-area worm deduplication, zero/invalid damage and packaged registry/tag parity.
- Downloaded the author-published Wither Storm 1.20.1-4.2.1 reference JAR for analysis only. Both its main entity and segment hurt methods explicitly forward bypass-invulnerability damage to the normal superclass damage path before their ordinary phase gates. Confirmed the obfuscated tag name against the local 1.20.1 mappings. The new spell type supplies that tag. The owner's deleted JAR version is unknown, and no real Wither Storm gameplay/regeneration test was run; this is not a promise to instantly kill every phase or private health system.
- Build and unit checks do not establish real gameplay compatibility. Owner tests with the specific boss-mod versions are required before claiming those combinations are verified.

Sources inspected: https://github.com/lender544/new1.20.1 , https://github.com/frozenhamburgers/desert-behemoths-sandworms and the official https://modrinth.com/mod/crackers-wither-storm-mod 4.2.1 release. Third-party source was used for API analysis only; no third-party implementation is bundled in God Wand.
