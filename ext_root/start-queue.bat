@echo off
rem Compiles the Java server into a temporary folder.
set "CLASSES=%TEMP%\netscope-url-queue"
if not exist "%CLASSES%" mkdir "%CLASSES%"
javac -d "%CLASSES%" "%~dp0HeapSort.java" "%~dp0UrlQueueServer.java"
if errorlevel 1 exit /b 1

rem Starts the local server used by the extension.
java -cp "%CLASSES%" UrlQueueServer
