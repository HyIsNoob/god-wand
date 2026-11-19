@echo off
rem Opens the Fabric 1.21.1 dev client to play-test (config in fabric\run\config).
cd /d "%~dp0"
set "JDKS=C:\Program Files\Microsoft\jdk-17.0.21.21-hotspot,C:\Program Files\Microsoft\jdk-21.0.8.9-hotspot,C:\Program Files\Microsoft\jdk-25.0.5.21-hotspot"
call gradlew.bat :fabric:runClient --offline "-Porg.gradle.java.installations.paths=%JDKS%"
pause
