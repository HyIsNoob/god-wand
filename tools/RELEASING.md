# Release God Wand on CurseForge

Requires Python 3.11+ and the normal Java/Gradle build toolchains.
Keep `CURSEFORGE_TOKEN` and `CURSEFORGE_PROJECT_ID=1760928` in the root `.env`.
The token is read directly by Python; it is never printed or passed through the shell.
`.env` remains ignored by Git.

Run `release.bat` for the menu, or from the repository root:

```powershell
.\release.bat --build --dry-run
.\release.bat --check-token
.\release.bat --upload
```

To build and upload in one command, use `.\release.bat --build --upload`.
For a beta, append `--release-type beta`.

The tool reads the current version and its changelog, validates the ten JARs
against `SHA256SUMS.txt` and embedded mod metadata, and assigns exact
Minecraft/loader tags plus both Client and Server environments. Fabric uploads declare Fabric API as required.
Preview is offline. Upload uses the [official CurseForge Upload API](https://support.curseforge.com/support/solutions/articles/9000197321).
No Modrinth or GitHub binaries are published, and the project description is not changed.

Successful uploads are recorded in `build/release/<version>/curseforge-uploads.json`;
rerunning skips the same project/filename/hash. Keep this local journal until the
release is complete. If the connection fails during upload, check CurseForge before
retrying: the server may have accepted the file before the connection failed.
Remote uploads made outside this tool are not recorded in the local journal.
CurseForge moderation still applies after upload.

Changelogs are sent as compact HTML lists, avoiding paragraph wrappers inside each bullet. Previously uploaded files can be edited under CurseForge Manage File. The documented update-file API returned HTTP 500 during verification, so this tool does not automate editing existing files.

## Branches and folders

One Git repo, one folder per line of work, all under `GodWand\`:

| Folder | Branch | Purpose |
| --- | --- | --- |
| `repo\` | `main` | Latest released code. Do not develop here; use it to release and to create worktrees. |
| `work\1.4.x\` | `1.4.x` | Fixes for the released 1.4 line (1.4.1, 1.4.2 ...). |
| `work\1.5.0\` | `1.5.0` | New skills and features for the next minor version. |

Create a worktree only when you need it, and remove it when the line is done:

```powershell
git worktree add ..\work\1.4.x 1.4.x                 # the branch already exists
git worktree add ..\work\1.5.0 -b 1.5.0 main         # a new line of work
git worktree remove ..\work\1.5.0                    # when finished
```

Release a version from its own folder: bump `mod_version` in `gradle.properties`, add the
`CHANGELOG.md` section, then run `release.bat` there. `.env` is shared automatically from `repo\`.
JARs are built into that folder's `build\release\<version>\`; copy them to `GodWand\releases\<version>`
as the permanent local copy.

After a 1.4.x release, merge `1.4.x` into `1.5.0` so the fixes are not lost (`git merge 1.4.x`).
When 1.5.0 is released, merge it into `main`. Create `1.5.x` from `main` at that point.
