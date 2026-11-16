# Blue/Red live QA (development only). Usage: powershell -NoProfile -File tools/gojo-qa/run.ps1 <combo|shatter|terrain> <log>
param([string]$scene = 'combo', [string]$log = 'build/fuga-qa/gojo.log')
Remove-Item Env:NoDefaultCurrentDirectoryInExePath -ErrorAction SilentlyContinue
$p='C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot,C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot,C:\Program Files\Microsoft\jdk-25.0.5.21-hotspot'
[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_org.gradle.java.installations.paths',$p,'Process')
$j='C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot\bin'
Set-Location (Split-Path -Parent (Split-Path -Parent $PSScriptRoot))
Set-Content -Path build/fuga-qa/gojo-scene -Value $scene -Encoding ascii
Remove-Item build/fuga-qa/stop -ErrorAction SilentlyContinue
# Every run digs real craters: start each one from the untouched world (build/fuga-qa/gojo-pristine, a copy of Gate QA).
if (Test-Path build/fuga-qa/gojo-pristine) { robocopy build/fuga-qa/gojo-pristine "build/fuga-qa/run/saves/Gate QA" /MIR /NFL /NDL /NJH /NJS /NP | Out-Null }
& .\gradlew.bat -I tools/fuga-qa.gradle :fabric:classes :fabric:writeFugaQaClasspath --offline --console=plain "-Porg.gradle.java.installations.paths=$p" *> $log
Remove-Item -Recurse -Force build/fuga-qa/gojo-classes -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force build/fuga-qa/gojo-classes | Out-Null
if (-not (Test-Path build/fuga-qa/classpath.txt)) { 'EXIT 2'; exit 2 }
& "$j\javac.exe" -proc:none --release 21 -cp (Get-Content build/fuga-qa/classpath.txt -Raw).Trim() -d build/fuga-qa/gojo-classes (Get-ChildItem tools/gojo-qa/*.java).FullName *>> $log
if ($LASTEXITCODE -ne 0) { 'EXIT javac'; exit 3 }
& "$j\jar.exe" cf build/fuga-qa/probe.jar -C build/fuga-qa/gojo-classes . -C tools/gojo-qa fabric.mod.json -C tools/gojo-qa gojo-qa.mixins.json
& .\gradlew.bat -I tools/fuga-qa.gradle :fabric:runClient --offline --console=plain "-Porg.gradle.java.installations.paths=$p" "-PfugaQaWorld=Gate QA" *>> $log
"EXIT $LASTEXITCODE"
