#!/bin/sh
# Copyright (C) 2005, International Business Machines
#   Corporation and others.  All Rights Reserved.

OS_INFO="`uname -a`"
OS_VERSION="`uname -r`"
OS_TYPE="`uname`"
ICU_ARGS="-I../../../../icu/source/common -L../../../../icu/source/lib -licuuc -licudata"
ICU_LIB_PATH="../../../../icu/source/lib"

echo "Collecting Unicode conversion mappings for $OS_TYPE"
case "$OS_TYPE" in
    AIX)
        FILES="genucm.cpp iconv.cpp"
        xlC_r $ICU_ARGS $FILES -liconv -o genucm
        LIBPATH=$ICU_LIB_PATH genucm $*
        ;;
    SunOS)
        FILES="genucm.cpp iconv.cpp"
        CC $ICU_ARGS $FILES -xtarget=ultra -xarch=v9 -mt -o genucm
        LD_LIBRARY_PATH=$ICU_LIB_PATH genucm $*
        ;;
    HP-UX)
        FILES="genucm.cpp iconv.cpp"
        aCC $ICU_ARGS +DD64 -licuuc -licudata -ldld $FILES -o genucm
        SHLIB_PATH=$ICU_LIB_PATH genucm $*
        ;;
    Linux)
        FILES="genucm.cpp iconv.cpp"
        g++ $ICU_ARGS $FILES -o genucm
        LD_LIBRARY_PATH=$ICU_LIB_PATH genucm $*
        ;;
    *)
        echo "Don't know how to get the list of mappings for $OS_TYPE"
        ;;
esac

