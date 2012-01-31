#!/bin/sh

# compile
gcc -c -fPIC -D_REENTRANT \
-DCOMPILE_STYLE=ANSI_C_COMPILE \
-fexceptions \
-I/opt/java6/include \
-I/opt/java6/include/linux \
-I/usr/cadc/misc/wcslib/include/wcslib \
-Ica/nrc/cadc/wcs \
ca/nrc/cadc/wcs/WCSLibTest.c -o ca/nrc/cadc/wcs/WCSLibTest.o

# link
gcc -shared \
ca/nrc/cadc/wcs/WCSLibTest.o \
/usr/cadc/misc/wcslib/lib/libwcs.a \
-o ca/nrc/cadc/wcs/libWCSLibTest.so
