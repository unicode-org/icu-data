#!/bin/sh

OS_INFO="`uname -a`"
OS_VERSION="`uname -r`"
OS_TYPE="`uname`"

# TODO: We should use `locale -m` to get the installed converters.


export OS_INFO OS_VERSION

echo "Collecting Unicode conversion mappings for $OS_TYPE"
case "$OS_TYPE" in
    AIX)
        gcc CharConv.c -liconv -o charConv
        /bin/ls /usr/lib/nls/loc/uconvTable/* | xargs charConv
        ;;
    SunOS)
        gcc CharConv.c -o charConv
        /bin/ls -F /usr/lib/iconv/UTF-8%*.so | grep -v @ | xargs charConv
        ;;
    HP-UX)
        cc -ldld CharConv.c -o charConv
        /bin/ls /usr/lib/nls/iconv/tables.1/ucs2=* | xargs charConv
        ;;
    Linux)
        gcc CharConv.c -o charConv
        /bin/ls -S /usr/lib/gconv/*.so | xargs charConv
#        /bin/ls -S /etc/charsets/* | fold | grep -v charsets.alias | xargs charConv
        ;;
    *)
        echo "Don't know how to get the list of mappings for $OS_TYPE"
        ;;
esac

# make sure to comment out NULL calls to iconv in CharConv.c

