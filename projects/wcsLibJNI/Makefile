#################################################################
#                                                               #
# Sample makefile for a system running Linux                    #
# using Java and the GNU gcc compiler.                          #
#                                                               #
#################################################################
SHELL = /bin/sh
.SUFFIXES: .c .o

#################################################################
#                                                               #
# The following variables are specific to the build environment #
# and must be set to the appropriate values.                    #
#                                                               #
#################################################################

#
# Compiler with debugging.
#
CC = gcc -g

#
# Path to the WCSLIB install. The path to the WCSLIB install can 
# be overridden by passing make an alternate path:
# make wcs=/path_to_wcslib
#
wcs = /usr

#
# Path to the WCSLIB library.
#
WCSLIB = $(wcs)/lib64/libwcs.so

#
# Directory containing the WCSLIB headers (but not including the wcslib/ subdir).
#
WCSLIB_HEADERS = $(wcs)/include

#
# Compiler flags.
#
FLAGS = \
-fPIC \
-D_REENTRANT \
-DCOMPILE_STYLE=ANSI_C_COMPILE \
-DCOMPILER_gnu \
-Dx86_linux \
-D_POSIX_C_SOURCE=2 \
-fexceptions \
-ansi \
-std=c99 \
-pedantic \
-Wall \
-Wno-parentheses \
-Wno-long-long \
-Wimplicit

#################################################################
#                                                               #
# The remaining variables are not system specific. The default  #
# values should suffice, but can be modified if desired.        #
#                                                               #
#################################################################

#
# Source file.
#
SRC = src/wcslib.c

#
# WcsLIB javah generated header.
#
WCSLIB_HEADER = h/wcslib.h

#
# Object file.
#
OBJECTS = obj/wcslib.o

#
# Shared library file.
#
SHARED = lib/libwcsLibJNI.so

#
# Include directories for compiling
#
INCLUDE_PATH = -I$(WCSLIB_HEADERS)

CFLAGS = \
$(FLAGS) \
$(INCLUDE_PATH)

#################################################################
#                                                               #
# Make targets                                                  #
#                                                               #
#################################################################
.PHONY: all
all:	mkdirs $(SHARED)

.PHONY: mkdirs
mkdirs: clean
	-mkdir obj lib

$(SHARED): \
	$(OBJECTS)
	$(CC) -shared $(CFLAGS) $(OBJECTS) $(WCSLIB) -o $(SHARED)

$(OBJECTS) : \
	$(SRC) $(WCSLIB_HEADER)
	$(CC) -c $(CFLAGS) $(SRC) -o $(OBJECTS)

.PHONY : clean
clean :
	-rm -fr obj lib
