# God Wand dedicated server

Install the matching Minecraft/loader God Wand JAR on both the server and every client. Fabric servers and clients also need Fabric API. Client graphics settings and external shaderpacks stay on the client. Do not install multiple loader/version JARs together. Update both server and clients to 1.3.3 together: per-skill cooldowns use a new server-to-client message.

God Wand server settings are in config/godwand-server.json; client options are in config/godwand-client.json and the K screen. Restart the world/server after changing server configuration.

## QA on 2026-10-27

Fabric/NeoForge 1.21.1 and Fabric/Forge 1.20.1 each booted a dedicated server from the integrated development source, loaded God Wand 1.3.0, generated an isolated QA world, reached Done, answered the RCON list command, ran additional ticks and saved/stopped cleanly. No client-only class-loading blocker occurred. Logs: build/dedicated-qa/<flavor>/launch.log and check.log. Test servers were restricted to loopback and are stopped.

The QA init script is tools/dedicated-qa.gradle. The local stop/check helper is tools/dedicated_qa_stop.py. These are development tools, excluded from release JARs. Forge server QA skips downloadAssets because the server does not need client textures/audio. The first NeoForge run needed its server-side Netty dependency downloaded before offline startup.

This verifies dedicated startup/ticking/shutdown in the development environment. Actual client joining, simultaneous Atomic casts, release JAR production bootstrap and compatibility with other server mods still need multiplayer testing.

## Atomic focus fix

Skip state is now scoped to the active cast and reset by the next local CHARGE packet. Losing focus or opening a screen suspends the cinematic camera instead of setting a future timestamp that blocks later casts. Closing the screen resumes the camera if the cast is still active. Esc/F5 still skip only the current cinematic. A regression covers focus loss, pause screen, resumption and an immediate new cast after skipping.

On a dedicated server, Alt-Tab does not pause server time: a cast can advance or complete while the client is away.

## Comparing camera and ambient gas

Compare the same JAR revision, K options (especially Post Shaders and Dynamic FOV), FOV, render distance and loaded terrain. Atomic volumetric ambient gas needs the mod's Post Shaders option, but does not require an external Iris/Oculus shaderpack. Camera distance is constrained by effective render distance, chunk availability and terrain obstruction. Source parity tests cover the shared camera, choreography, geometry and shader resources; pixel-identical output across loaders/shaderpacks has not been established.
