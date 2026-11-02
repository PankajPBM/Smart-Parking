@echo off
setlocal enabledelayedexpansion

echo ===================================================
echo     SMART PARKING - GENERATE STANDALONE APK
echo           PB Tech Zone (SUNMI POS)
echo ===================================================
echo.

REM Automatically detect and set JAVA_HOME if not already configured
if "%JAVA_HOME%"=="" (
    if exist "C:\Users\Bivek\.antigravity\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64" (
        set "JAVA_HOME=C:\Users\Bivek\.antigravity\extensions\redhat.java-1.54.0-win32-x64\jre\21.0.10-win32-x86_64"
    ) else if exist "C:\Program Files\Android\Android Studio\jbr" (
        set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    )
)

echo Using JAVA_HOME: %JAVA_HOME%
echo.
echo Building Standalone Debug APK for SUNMI POS...
echo.

call gradlew.bat assembleDebug

if %ERRORLEVEL% equ 0 (
    echo.
    echo ===================================================
    echo [SUCCESS] APK BUILD COMPLETED!
    echo ===================================================
    echo.
    
    if exist "app\build\outputs\apk\debug\app-debug.apk" (
        copy /Y "app\build\outputs\apk\debug\app-debug.apk" "..\SmartParking-SUNMI-v1.0.0.apk" >nul
        copy /Y "app\build\outputs\apk\debug\app-debug.apk" "SmartParking-SUNMI-v1.0.0.apk" >nul
        echo Primary APK Output:
        echo   1) %CD%\SmartParking-SUNMI-v1.0.0.apk
        echo   2) %CD%\..\SmartParking-SUNMI-v1.0.0.apk
        echo   3) %CD%\app\build\outputs\apk\debug\app-debug.apk
        echo.
        echo You can now copy SmartParking-SUNMI-v1.0.0.apk to your SUNMI POS terminal or phone.
    )
) else (
    echo.
    echo [ERROR] Build failed with error code %ERRORLEVEL%. Please review logs above.
)

echo.
pause
