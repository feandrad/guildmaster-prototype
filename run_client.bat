@echo off
setlocal enabledelayedexpansion

REM Default server settings
set SERVER=127.0.0.1
set TCP_PORT=9999
set UDP_PORT=9998

REM Parse command-line arguments
:parse_args
if "%~1"=="" goto :done_args
if "%~1"=="-s" (
    set SERVER=%~2
    shift
    shift
    goto :parse_args
)
if "%~1"=="--server" (
    set SERVER=%~2
    shift
    shift
    goto :parse_args
)
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
    echo Guild Master Client
    echo Usage: %0 [options]
    echo Options:
    echo   -s ^<address^>  Server address (default: 127.0.0.1)
    echo   -t ^<port^>     TCP port (default: 9999)
    echo   -u ^<port^>     UDP port (default: 9998)
    echo   -h            Show this help message
    exit /b 0
)
if "%~1"=="--help" (
    echo Guild Master Client
    echo Usage: %0 [options]
    echo Options:
    echo   -s ^<address^>  Server address (default: 127.0.0.1)
    echo   -t ^<port^>     TCP port (default: 9999)
    echo   -u ^<port^>     UDP port (default: 9998)
    echo   -h            Show this help message
    exit /b 0
)
shift
goto :parse_args
:done_args

REM Navigate to client directory
cd client

REM Check if build directory exists, if not create it
if not exist "build" (
    mkdir build
)

REM Navigate to build directory
cd build

REM Run CMake if CMakeCache.txt doesn't exist
if not exist "CMakeCache.txt" (
    echo Configuring with CMake...
    cmake ..
)

REM Compile the project
echo Compiling client...
cmake --build . --config Release

REM Check if compilation was successful
if %ERRORLEVEL% neq 0 (
    echo Compilation failed!
    exit /b 1
)

REM Run the client with server settings
echo Starting Guild Master client...
echo Connecting to server: %SERVER% (TCP: %TCP_PORT%, UDP: %UDP_PORT%)
Release\guildmaster_client.exe --server "%SERVER%" --tcp-port "%TCP_PORT%" --udp-port "%UDP_PORT%" 