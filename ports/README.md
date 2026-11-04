# Minecraft ports

God Wand **1.4.0** has separate Fabric and NeoForge builds for three newer Minecraft versions:

- [mc-1.21.11](mc-1.21.11) — Minecraft 1.21.11.
- [mc-26.1.2](mc-26.1.2/README.md) — Minecraft 26.1.2, derived from the 26.3 port with vanilla 26.1.2 rendering (conventional depth, blaze3d `GpuBuffer`/`RenderPipeline`).
- [mc-26.3](mc-26.3/README.md) — Minecraft 26.3.

All three include all seven skills, including Fuga, and the current balance settings.

Root `releaseJars` builds and checks all ten targets, including these nested Java 25 builds. All consume canonical common sources and strict API adapters; see [shared-source ownership](../docs/SHARED_SOURCES.md). Builds and automated checks do not replace gameplay, shaderpack or multiplayer testing.
