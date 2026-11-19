@echo off
setlocal

set "classes=%TEMP%\netscope-analyzer-classes"
if exist "%classes%" rmdir /s /q "%classes%"
mkdir "%classes%"
javac -Xlint:all -d "%classes%" "%~dp0src\main\java\com\netscope\analysis\*.java"
if errorlevel 1 exit /b %errorlevel%
java -Dnetscope.badDomains="%~dp0known-malicious-domains.txt" -cp "%classes%" com.netscope.analysis.NetScopeServer
