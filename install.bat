@echo off
cd /d "%~dp0"
./gradlew assembleDebug
adb install -r app/build/outputs/apk/foss/debug/launcher-13-foss-debug.apk