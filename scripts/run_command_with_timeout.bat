@echo off
REM usage: run_command_with_timeout.bat timeout command
REM where:
REM timeout is the timeout in minutes
REM command is the command to run
REM author: Flavia Rainone


set "command=%~2"
echo %command%
set "lock=%temp%\%~nx0.lock"

start "" /b %command% 9>"%lock%"
set /a count=0

:wait
REM waited too long, kill the test if it is there
if %count%==%1 (goto :killHangingTest)
timeout /t 60 /nobreak >nul
set /a count=%count% + 1
2>nul (9>"%lock%" (call ) && del "%lock%" || goto :wait)
echo No hanging test found, tests ran successfully
exit 0

:killHangingTest
FOR /F "tokens=*" %%A IN ('jps^|awk "$2 ~ /surefirebooter/ {print $1}"') DO SET PID=%%A
echo Hanging test found. Pid is %PID%
jstack -l %PID% > thread-dump.txt & taskkill /F /PID %PID% & exit 1