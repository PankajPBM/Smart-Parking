@echo off
echo ===================================================
echo     SMART PARKING - ONE-CLICK BUILD ^& INSTALL
echo           PB Tech Zone (SUNMI POS POS)
echo ===================================================
echo.
echo [1/3] Checking connected SUNMI / Android devices...
adb devices
echo.
echo [2/3] Compiling and Building Debug APK...
call gradlew.bat assembleDebug
if %ERRORLEVEL% NEQ 0 (
    echo [ERROR] Build failed. Please check the error above.
    pause
    exit /b %ERRORLEVEL%
)
echo.
echo [3/3] Installing and Launching Smart Parking App...
call gradlew.bat installDebug
adb shell am start -n com.smartparking.app/.MainActivity
echo.
echo ===================================================
echo   SUCCESS: Smart Parking is now running on device!
echo ===================================================
pause
