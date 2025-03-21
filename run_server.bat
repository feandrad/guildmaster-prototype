@echo off
setlocal enabledelayedexpansion

REM Default port settings
set TCP_PORT=9999
set UDP_PORT=9998

REM Parse command-line arguments
:parse_args
if "%~1"=="" goto :done_args
if "%~1"=="-t" (
    set TCP_PORT=%~2
    shift
    shift
    goto :parse_args
)
if "%~1"=="--tcp-port" (
    set TCP_PORT=%~2
    shift
    shift
    goto :parse_args
)
if "%~1"=="-u" (
    set UDP_PORT=%~2
    shift
    shift
    goto :parse_args
)
if "%~1"=="--udp-port" (
    set UDP_PORT=%~2
    shift
    shift
    goto :parse_args
)
if "%~1"=="-h" (
    echo Guild Master Server
    echo Usage: %0 [options]
    echo Options:
    echo   -t ^<port^>     TCP port (default: 9999)
    echo   -u ^<port^>     UDP port (default: 9998)
    echo   -h            Show this help message
    exit /b 0
)
if "%~1"=="--help" (
    echo Guild Master Server
    echo Usage: %0 [options]
    echo Options:
    echo   -t ^<port^>     TCP port (default: 9999)
    echo   -u ^<port^>     UDP port (default: 9998)
    echo   -h            Show this help message
    exit /b 0
)
shift
goto :parse_args
:done_args

REM Navigate to server directory
cd server

REM Build the server
echo Building Guild Master server...
call gradlew.bat build

REM Check if build was successful
if %ERRORLEVEL% neq 0 (
    echo Build failed!
    exit /b 1
)

REM Run the server with the specified ports
echo Starting Guild Master server on TCP port %TCP_PORT% and UDP port %UDP_PORT%...
call gradlew.bat run --args="--server.tcp.port=%TCP_PORT% --server.udp.port=%UDP_PORT%" 