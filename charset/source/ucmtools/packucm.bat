@echo This tool can be used to create a package of converters for all *.ucm files
@echo  in the current directory.
@echo Syntax: packucm [ package_prefix ]

@echo off > %TEMP%\templist.txt
setlocal
set ICUDATA_PACKAGE=%1
if "%1" == "" set ICUDATA_PACKAGE=icudata
@echo Creating a package for %ICUDATA_PACKAGE%
for %%i in (%1*.ucm) do makeconv -p %ICUDATA_PACKAGE% %%i && echo %ICUDATA_PACKAGE%_%%~ni.cnv >> %TEMP%\templist.txt
pkgdata -p %ICUDATA_PACKAGE% -m common -O R:C:\Development\icu\ %TEMP%\templist.txt
endlocal
del %TEMP%\templist.txt