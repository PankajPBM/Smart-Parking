@echo off
echo ===================================================
echo     SMART PARKING - GENERATE STANDALONE APK
echo           PB Tech Zone (SUNMI POS)
echo ===================================================
echo.
echo Building Debug APK...
call gradlew.bat assembleDebug
echo.
echo ===================================================
echo APK generated at:
echo app\build\outputs\apk\debug\app-debug.apk
echo ===================================================
pause
