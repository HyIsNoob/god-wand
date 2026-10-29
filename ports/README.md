# Minecraft ports

God Wand **1.3.2** has separate **Fabric and NeoForge 26.3** test builds under [mc-26.3](mc-26.3/README.md). Both include all six skills and current balance settings. Builds and automated checks pass; owner gameplay testing is pending.

**Minecraft 1.21.11 was started, then paused for the 1.3.2 boss-damage release fix.** Its local prototype is not release-ready and is excluded from Git. See [1.21.11 notes](mc-1.21.11/PORT_STATUS.md).

The root build owns the four existing 1.21.1/1.20.1 variants. The 26.3 project has its own Java 25 build and is not included in root releaseJars. PR #1 proposes changing that build setup and remains separate from the license and port changes.

The new boss-damage patch currently applies only to the four root 1.20.1/1.21.1 variants. Bring it into the 26.3/1.21.11 ports before publishing those previews.
