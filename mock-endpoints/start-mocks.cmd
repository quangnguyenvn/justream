@echo off
setlocal
cd /d "%~dp0"

set HOST=127.0.0.1
set PATHNAME=/ws

start "baseline-mock" /min cmd /c node echo-server.js --name=baseline-mock --host=%HOST% --port=18081 --path=%PATHNAME% --delayMs=2 1>baseline-mock.out.log 2>baseline-mock.err.log
start "candidate-mock" /min cmd /c node echo-server.js --name=candidate-mock --host=%HOST% --port=18082 --path=%PATHNAME% --delayMs=0 1>candidate-mock.out.log 2>candidate-mock.err.log

echo.
echo Mock endpoints started.
echo baseline  = ws://%HOST%:18081%PATHNAME%
echo candidate = ws://%HOST%:18082%PATHNAME%
echo health baseline  = http://%HOST%:18081/health
echo health candidate = http://%HOST%:18082/health

endlocal
