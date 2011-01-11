#!/bin/sh
# Copyright (C) 2005-2011, International Business Machines
#   Corporation and others.  All Rights Reserved.

OS_INFO="`uname -a`"
OS_VERSION="`uname -r`"
OS_TYPE="`uname`"
if [ ! -d "${ICU_SRC}" -o ! -d "${ICU_SRC}/common" ];
then
	echo Error cannot find "${ICU_SRC}/common" - make sure '$ICU_SRC' is set and points to your icu/source directory.
	exit 1
fi


ICU_LIB_PATH="${ICU_SRC}/lib"
ICU_ARGS="-I${ICU_SRC}/common -L${ICU_LIB_PATH} -licuuc -licudata"

echo "Collecting Unicode conversion mappings for $OS_TYPE"
rm -f genucm
case "$OS_TYPE" in
    AIX)
        FILES="genucm.cpp iconv.cpp"
        xlC_r $ICU_ARGS $FILES -liconv -o genucm
        LIBPATH=$ICU_LIB_PATH ./genucm $*
        ;;
    SunOS)
        FILES="genucm.cpp iconv.cpp"
        CC $ICU_ARGS $FILES -xtarget=ultra -xarch=v9 -mt -o genucm
        LD_LIBRARY_PATH=$ICU_LIB_PATH ./genucm $*
        ;;
    HP-UX)
        FILES="genucm.cpp iconv.cpp"
        aCC $ICU_ARGS +DD64 -licuuc -licudata -ldld $FILES -o genucm
        SHLIB_PATH=$ICU_LIB_PATH ./genucm $*
        ;;
    Linux)
        FILES="genucm.cpp iconv.cpp"
        g++ $ICU_ARGS $FILES -o genucm
        LD_LIBRARY_PATH=$ICU_LIB_PATH ./genucm $*
        ;;
    Darwin)
        FILES="genucm.cpp mactec.cpp"
        g++ $ICU_ARGS $FILES -framework CoreServices -o genucm
        DYLD_LIBRARY_PATH=$ICU_LIB_PATH ./genucm $*
        ;;
    FreeBSD)
        FILES="genucm.cpp iconv.cpp"
        g++ $ICU_ARGS -I/usr/local/include -L/usr/local/lib $FILES -liconv -o genucm
        LD_LIBRARY_PATH=$ICU_LIB_PATH ./genucm $*
        ;;
    *)
        echo "Don't know how to get the list of mappings for $OS_TYPE"
        ;;
esac

