// © 2016 and later: Unicode, Inc. and others.
// License & terms of use: http://www.unicode.org/copyright.html#License

// Copyright (c) 2006 IBM and Others. All Rights Reserved

This directory contains updated zoneinfo.txt, binary versions, and a Makefile which will
automatically fetch new tzdata and compile it.

10 second setup for building new versions of the data:
------------------------------------------------------

0. build and install ICU somewhere.

1. create Makefile.local and point it at your ICU source dir. I use  "ICU_SRC=/xsrl/W/icu/source".

2. if icu-config isn't on your PATH, you can set ICU_CONFIG in Makefile.local to the address of your installed icu-config

3. put updated updated metaZones.txt, timezoneTyes.txt amd wondowsZones.txt (also files for older ICU version) under supplemental directory

4. now run:
4a.  "make TZDBVER=<version> update" to fetch latest tzdata/code specified by <version> (e.g. 2018b) via FTP, and build it
4b.  or just "make TZDBVER=<version> update-mirror" to only fetch via FTP
4c.  or just "make TZDBVER=<version> update-icu" to only update the icu4c with any tzdata in your mirror directory

5. add files generated under icunew/<version> and supplemental files to svn
