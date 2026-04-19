@echo off
setlocal

for %%P in (18081 18082) do (
  for /f "tokens=5" %%I in ('netstat -ano ^| findstr "LISTENING" ^| findstr ":%%P"') do (
    taskkill /PID %%I /F >nul 2>&1
  )
)

echo Stopped listeners on ports 18081 and 18082 (if any).

endlocal
