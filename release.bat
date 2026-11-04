@echo off
setlocal
cd /d "%~dp0"
chcp 65001 >nul
set "PYTHONUTF8=1"
if not "%~1"=="" (
    python tools\release.py %*
    exit /b
)
:menu
echo.
echo God Wand - CurseForge Release
echo [1] Build all 8 JARs and preview
echo [2] Preview current JARs (offline dry run)
echo [3] Check CurseForge token (no upload)
echo [4] Upload current JARs to CurseForge
echo [0] Exit
set "choice="
set /p choice="Choose: "
if "%choice%"=="0" exit /b 0
if "%choice%"=="1" python tools\release.py --build --dry-run
if "%choice%"=="2" python tools\release.py --dry-run
if "%choice%"=="3" python tools\release.py --check-token
if "%choice%"=="4" python tools\release.py --upload
pause
goto menu
