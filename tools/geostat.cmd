@echo off
REM Windows CMD entry — .ps1 from cmd opens Notepad unless run via powershell.exe -File
setlocal
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0geostat.ps1" %*
exit /b %ERRORLEVEL%
