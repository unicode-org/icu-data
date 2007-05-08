@echo off
rem Copyright Copyright (C) 2000-2006, International Business Machines Corporation and others.
rem All Rights Reserved.
rem echo Remember to put the rptp2ucm program in the PATH
for %%i in (*.r?map*) do echo Converting %%~fi && rptp2ucm -f ../rptp-history.txt %%i
