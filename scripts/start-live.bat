@echo off
chcp 65001 >nul
cd /d "%~dp0.."
title CIA - Live Reload

where node >nul 2>nul
if errorlevel 1 (
  echo Node.js نسخه 22 يا جديدتر نصب نيست.
  echo راهنما: https://nodejs.org/
  pause
  exit /b 1
)

if not exist node_modules (
  echo در حال نصب وابستگي‌هاي پروژه...
  call npm install
  if errorlevel 1 goto failed
)

if not exist android\capacitor.settings.gradle (
  echo پروژه اندرويد Capacitor هنوز ساخته نشده است.
  echo ابتدا بايد يک بار npx cap add android اجرا شود.
  pause
  exit /b 1
)

for /f "delims=" %%I in ('powershell -NoProfile -Command "$c=Get-NetIPConfiguration | Where-Object {$_.NetAdapter.Status -eq 'Up' -and $_.IPv4Address -and $_.InterfaceAlias -match 'Wi-Fi'} | Select-Object -First 1; if($c){$c.IPv4Address.IPAddress}"') do set LAN_IP=%%I

if "%LAN_IP%"=="" (
  echo آدرس Wi-Fi به طور خودکار پيدا نشد.
  echo آدرس IPv4 را با دستور ipconfig پيدا کنيد.
  set /p LAN_IP=آدرس IPv4 کامپيوتر را وارد کنيد: 
)

echo.
echo آدرس سرور توسعه: http://%LAN_IP%:5173
echo گوشی و کامپيوتر بايد به يک Wi-Fi وصل باشند.
echo.

call npm run build
if errorlevel 1 goto failed

start "CIA Web Dev Server" cmd /k "cd /d "%CD%" && npm run dev"
timeout /t 3 /nobreak >nul

echo اکنون برنامه آزمايشي روي دستگاه اندرويدي نصب/اجرا مي‌شود.
call npx cap run android --live-reload --host "%LAN_IP%" --port 5173
if errorlevel 1 goto failed
exit /b 0

:failed
echo.
echo اجرا با خطا روبه‌رو شد. متن خطا را براي بررسي نگه داريد.
pause
exit /b 1
