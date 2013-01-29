#!/bin/sh

# compile
gcc -c -fPIC -D_REENTRANT \
-DCOMPILE_STYLE=ANSI_C_COMPILE \
-fexceptions \
-I/usr/include/wcslib \
-Ica/nrc/cadc/wcs \
ca/nrc/cadc/wcs/WCSLibTest.c -o ca/nrc/cadc/wcs/WCSLibTest.o

# link
gcc -shared \
ca/nrc/cadc/wcs/WCSLibTest.o \
/usr/lib64/libwcs.so \
-o ca/nrc/cadc/wcs/libWCSLibTest.so
