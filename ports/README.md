# Minecraft ports

God Wand **1.3.2** has separate **Fabric and NeoForge 26.3** test builds under [mc-26.3](mc-26.3/README.md). Both include all six skills and current balance settings. Builds and automated checks pass; owner gameplay testing is pending.

**Minecraft 1.21.11 was started, then paused for the 1.3.2 boss-damage release fix.** Its local prototype is not release-ready and is excluded from Git.

Root `releaseJars` builds and checks all six targets, including the nested Java 25 build. All consume canonical common sources and strict API adapters; see [shared-source ownership](../docs/SHARED_SOURCES.md).

Boss damage fixes, latest visual/menu changes and terrain scheduling are synchronized to 26.3. The unfinished 1.21.11 prototype is excluded.
