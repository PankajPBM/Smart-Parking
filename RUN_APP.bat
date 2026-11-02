@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo     SMART PARKING - ONE-CLICK BUILD ^& INSTALL
echo           PB Tech Zone (SUNMI POS POS)
echo ===================================================
echo.

if "%JAVA_HOME%"=="" (
    if exist "C:\Users\Bivek\.antigravity\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64" (
        set "JAVA_HOME=C:\Users\Bivek\.antigravity\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64"
    ) else if exist "C:\Program Files\Android\Android Studio\jbr" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    )
)

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
