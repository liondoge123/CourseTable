@echo off
setlocal
where pwsh >nul 2>nul
if %ERRORLEVEL% equ 0 (
    pwsh -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1" %*
    exit /b %ERRORLEVEL%
)

if exist "D:\PowerShell7\7\pwsh.exe" (
    "D:\PowerShell7\7\pwsh.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1" %*
    exit /b %ERRORLEVEL%
)

if exist "%ProgramFiles%\PowerShell\7\pwsh.exe" (
    "%ProgramFiles%\PowerShell\7\pwsh.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1" %*
    exit /b %ERRORLEVEL%
)

if exist "%ProgramFiles(x86)%\PowerShell\7\pwsh.exe" (
    "%ProgramFiles(x86)%\PowerShell\7\pwsh.exe" -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1" %*
    exit /b %ERRORLEVEL%
)

echo [WARN] PowerShell 7 (pwsh) not found in PATH or standard directories. Falling back to Windows PowerShell 5.1...
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0build-apk.ps1" %*
exit /b %ERRORLEVEL%
