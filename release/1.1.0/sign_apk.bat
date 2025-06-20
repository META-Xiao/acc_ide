@echo off
echo === ACC IDE APK Signing Tool ===
echo.

set APK_FILE=acc_ide-1.0.0-unsigned.apk
set SIGNED_APK=acc_ide-1.0.0-signed.apk
set ALIGNED_APK=acc_ide-1.0.0.apk

REM Check if the input file exists
if not exist %APK_FILE% (
    echo Error: %APK_FILE% not found in current directory!
    goto :end
)

REM Get keystore information
set /p KEYSTORE_PATH=Enter path to your keystore: 
if not exist %KEYSTORE_PATH% (
    echo Error: Keystore file not found!
    goto :end
)

set /p KEY_ALIAS=Enter keystore alias: 
set /p KEYSTORE_PASSWORD=Enter keystore password: 
set /p KEY_PASSWORD=Enter key password (press Enter if same as keystore password): 

REM If key password is empty, use keystore password
if "%KEY_PASSWORD%"=="" set KEY_PASSWORD=%KEYSTORE_PASSWORD%

REM Sign the APK
echo Signing APK...
call jarsigner -verbose -sigalg SHA1withRSA -digestalg SHA1 -keystore "%KEYSTORE_PATH%" ^
    -storepass "%KEYSTORE_PASSWORD%" -keypass "%KEY_PASSWORD%" ^
    -signedjar "%SIGNED_APK%" "%APK_FILE%" "%KEY_ALIAS%"

if %ERRORLEVEL% neq 0 (
    echo Error: APK signing failed!
    goto :end
)

REM Check if zipalign is available
where zipalign >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo Aligning the APK...
    call zipalign -v 4 "%SIGNED_APK%" "%ALIGNED_APK%"
    
    if %ERRORLEVEL% equ 0 (
        echo APK successfully signed and aligned: %ALIGNED_APK%
    ) else (
        echo Warning: APK alignment failed. Using unaligned APK: %SIGNED_APK%
    )
) else (
    echo Warning: zipalign tool not found. Using unaligned APK: %SIGNED_APK%
)

echo.
echo The APK is now ready for distribution.
echo =============================================

:end
pause 