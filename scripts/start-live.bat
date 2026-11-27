@echo off
chcp 65001 >nul
cd /d "%~dp0.."
title CIA - Live Reload

where node >nul 2>nul
if errorlevel 1 (
  echo Node.js نصب نیست. ابتدا Node.js نسخه 22 یا جدیدتر را نصب کنید.
  echo راهنما: https://nodejs.org/
  pause
  exit /b 1
)

if not exist node_modules (
  echo در حال نصب وابستگی‌های پروژه...
  call npm install
  if errorlevel 1 goto failed
)

if not exist android\capacitor.settings.gradle (
  echo پوشه اندروید Capacitor هنوز ساخته نشده است.
  echo ابتدا یک بار دستور npx cap add android اجرا شود.
  pause
  exit /b 1
)

echo.
echo برای Live Reload، گوشی باید به همین Wi-Fi متصل و USB debugging فعال باشد.
echo در حال اجرای برنامه آزمایشی...
call npm run android:live
if errorlevel 1 goto failed
exit /b 0

:failed
echo.
echo اجرای CIA با خطا روبه‌رو شد. متن خطا را برای بررسی نگه دارید.
pause
exit /b 1
