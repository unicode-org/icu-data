@echo off
rem Copyright Copyright (C) 2000-2006, International Business Machines Corporation and others.
rem All Rights Reserved.

rem echo Remember to put the rptp2ucm and winzip32.exe programs in your PATH
echo Remember to put the rptp2ucm program in your PATH

rem javac CDRAbot.java
rem java CDRAbot

rem Unzip each zip file to each directory
rem for /d %%i in (*.cdra) do del /q/f %%~fi\*.* && rmdir %%~fi
rem for %%i in (*.zip) do mkdir %%~ni.cdra && winzip32 -e -j %%~fi %%~ni.cdra && echo %%~fi

for /d %%i in (cdra) do cd %%i && call ..\cdra2ucm.bat && move *.ucm .. && cd ..

rem Delete all of the directories just created.
rem for /d %%i in (*.cdra) do del /q/f %%~fi\*.* && rmdir %%~fi
