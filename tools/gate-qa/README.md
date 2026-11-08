# Silent Gate live QA (development only)

Drives mode 7 in the real Fabric 1.21.1 client in a separate window and captures native render-target frames.
Reuses `tools/fuga-qa.gradle`. Run dir `build/fuga-qa/run`, world `saves/Gate QA` (copy of the flat Fuga QA world), output `build/fuga-qa/run/screenshots/gate-<run>/f-NNNN.png`.
Create `build/fuga-qa/stop` to stop the game. Needs the Microsoft JDK paths from the build recipe (Java 21 for the run).

```powershell
Remove-Item Env:NoDefaultCurrentDirectoryInExePath -ErrorAction SilentlyContinue
$p='C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot,C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot,C:\Program Files\Microsoft\jdk-25.0.5.21-hotspot'
[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_org.gradle.java.installations.paths',$p,'Process')
$j='C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot\bin'
# 1. classpath, 2. compile with Java 21, 3. package, 4. run
.\gradlew.bat -I tools/fuga-qa.gradle :fabric:writeFugaQaClasspath --offline "-Porg.gradle.java.installations.paths=$p"
New-Item -ItemType Directory -Force build/fuga-qa/gate-classes | Out-Null
& "$j\javac.exe" -proc:none --release 21 -cp (Get-Content build/fuga-qa/classpath.txt -Raw).Trim() -d build/fuga-qa/gate-classes (Get-ChildItem tools/gate-qa/*.java).FullName
& "$j\jar.exe" cf build/fuga-qa/probe.jar -C build/fuga-qa/gate-classes . -C tools/gate-qa fabric.mod.json -C tools/gate-qa gate-qa.mixins.json
.\gradlew.bat -I tools/fuga-qa.gradle :fabric:runClient --offline --console=plain "-Porg.gradle.java.installations.paths=$p" -PfugaQaWorld="Gate QA"
```

Contact sheet (4 columns, needs Pillow): `python tools/gate-qa/sheet.py <screenshots dir> <out.png> <firstFrame> <lastFrame> <step>`
