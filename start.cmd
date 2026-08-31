@echo off
REM Double-click launcher.
REM
REM Running start.ps1 directly from Explorer opens a window that closes the
REM instant the script ends, taking any error message with it. -NoExit keeps
REM the window open so you can read what happened.
powershell -NoExit -NoProfile -ExecutionPolicy Bypass -File "%~dp0start.ps1" %*
