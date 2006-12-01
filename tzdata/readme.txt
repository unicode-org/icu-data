// Copyright (c) 2006 IBM and Others. All Rights Reserved

icutz readme


10 second setup:
----------------

0. build and install ICU somewhere.

1. create Makefile.local and point it at your ICU source dir. I use  "ICU_SRC=/xsrl/W/icu/source".

2. if icu-config isn't on your PATH, you can set ICU_CONFIG in Makefile.local to the address of your installed icu-config

3. now run:
3a.  "make update" to fetch latest tzdata via FTP, and build it
3b.  or just "make update-mirror" to only fetch via FTP
3c.  or just "make update-icu4c" to only update the icu4c with any tzdata in your mirror directory

4.  run "svn commit" to check the results back in.
