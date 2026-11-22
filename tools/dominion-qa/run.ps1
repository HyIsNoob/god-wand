# Block Dominion live QA & demo video recorder (development only).
# Usage: powershell -NoProfile -File tools/dominion-qa/run.ps1
param([string]$log = 'build/dominion-qa/qa.log')
Remove-Item Env:NoDefaultCurrentDirectoryInExePath -ErrorAction SilentlyContinue
$p = 'C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot,C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot,C:\Program Files\Microsoft\jdk-25.0.5.21-hotspot'
[Environment]::SetEnvironmentVariable('ORG_GRADLE_PROJECT_org.gradle.java.installations.paths', $p, 'Process')
$j = 'C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot\bin'
$root = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
Set-Location $root

New-Item -ItemType Directory -Force build/dominion-qa | Out-Null
Remove-Item -Recurse -Force build/dominion-qa/classes, build/dominion-qa/run/screenshots/dominion-frames, build/dominion-qa/run/screenshots/dominion-keyframes -ErrorAction SilentlyContinue
Remove-Item -Force build/dominion-qa/run/config/godwand-server.json -ErrorAction SilentlyContinue
Remove-Item -Recurse -Force "build/dominion-qa/run/saves/Dominion QA" -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force build/dominion-qa/classes, build/dominion-qa/run/screenshots/dominion-frames, build/dominion-qa/run/screenshots/dominion-keyframes | Out-Null
Set-Content -Path build/dominion-qa/run/options.txt -Value "onboardAccessibility:false`npauseOnLostFocus:false`ntutorialStep:none`n" -Encoding ascii

& .\gradlew.bat -I tools/dominion-qa.gradle :fabric:classes :fabric:writeDominionQaClasspath --offline --console=plain "-Porg.gradle.java.installations.paths=$p" *> $log
if ($LASTEXITCODE -ne 0) { 'EXIT gradle-prep'; exit 1 }
if (-not (Test-Path build/dominion-qa/classpath.txt)) { 'EXIT no-classpath'; exit 2 }

& "$j\javac.exe" -proc:none --release 21 -cp (Get-Content build/dominion-qa/classpath.txt -Raw).Trim() -d build/dominion-qa/classes (Get-ChildItem tools/dominion-qa/*.java).FullName *>> $log
if ($LASTEXITCODE -ne 0) { 'EXIT javac'; exit 3 }

& "$j\jar.exe" cf build/dominion-qa/probe.jar -C build/dominion-qa/classes . -C tools/dominion-qa fabric.mod.json -C tools/dominion-qa dominion-qa.mixins.json
if ($LASTEXITCODE -ne 0) { 'EXIT jar'; exit 4 }

& .\gradlew.bat -I tools/dominion-qa.gradle :fabric:runClient --offline --console=plain "-Porg.gradle.java.installations.paths=$p" *>> $log
$mcExit = $LASTEXITCODE

# Encode captured frames into crisp 20 FPS MP4 demo video
$framesPath = "build/dominion-qa/run/screenshots/dominion-frames"
$frameFiles = Get-ChildItem $framesPath -Filter "f-*.png" -ErrorAction SilentlyContinue
if ($frameFiles.Count -gt 10) {
    ffmpeg -y -v error -framerate 20 -i "$framesPath/f-%04d.png" -c:v libx264 -pix_fmt yuv420p -crf 20 -movflags +faststart build/dominion-qa/demo-block-dominion.mp4
    Write-Host "VIDEO: build/dominion-qa/demo-block-dominion.mp4 ($($frameFiles.Count) frames)"
}
"EXIT $mcExit"
exit $mcExit
