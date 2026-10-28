# Minecraft ports

Fabric ports for modern versions:
- **Fabric 26.1.2** is tracked in [mc-26.1.2](mc-26.1.2/README.md); configured for Minecraft 26.1.2 with Fabric API 0.155.3+26.1.2 and Java 25.
- **Fabric 26.3** is tracked in [mc-26.3](mc-26.3/README.md); all six skills and the current balance settings are included. Build and automated checks pass; owner gameplay testing is pending.

**Pending by owner request:** NeoForge (26.1.2, 26.3) and Fabric/NeoForge 1.21.11. Do not resume either alongside Fabric testing. The old local prototypes are not release builds; their source is excluded from Git. See [1.21.11 notes](mc-1.21.11/PORT_STATUS.md).

The root build still owns the four existing 1.21.1/1.20.1 variants. The 26.x projects have their own Java 25 builds; they are not included in root releaseJars.
