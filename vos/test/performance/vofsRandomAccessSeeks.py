#!/usr/bin/env python2.7

import os
import optparse
import time
import array
import random

class Main:
    @staticmethod
    def parseCmdLine():
        op = optparse.OptionParser(description='vofsRandomAccessSeeks.py')
        op.add_option("--size", type='int', default=1, help='Block size in KB' )
        op.add_option("--offsets", type='string', default='0', \
                help='White space separated list of read offsets in KB' )
        op.add_option("--random", type='int', default=None, \
                help='Number of random seeks into the file' )
        op.add_option("--delay", type='float', default=0, \
                help='Delay (fractional seconds) between seeks' )
        op.add_option("--fileName", type='string', default='-', \
                help='File to read.' )
        Main.opt, args = op.parse_args()
        if ( Main.opt.fileName == '-' ):
            print "ERROR: Filename must be provided." 
            op.print_help()
            exit( -1 )

        if ( Main.opt.random != None ):
            # Get the file size
            statinfo = os.stat(Main.opt.fileName)
            maxValue = ( statinfo.st_size  / 1024 ) - Main.opt.size
            Main.opt.offsets = " ".join( str(w) for w in \
                    random.sample( xrange(maxValue), Main.opt.random) )
            print Main.opt.offsets
        else:
            for offset in Main.opt.offsets.split():
                if ( not offset.isdigit() ):
                    print "ERROR: Offsets must be integers"
                    op.print_help()
                    exit( -1 )


    @staticmethod
    def mainMethod():
        Main.parseCmdLine()

        timming = array.array('f')
        start = time.time()
        testFile = open( Main.opt.fileName, 'r' )
        end = time.time()
        timming.append( end - start )

        for offset in Main.opt.offsets.split():
            if ( Main.opt.delay != None ):
                time.sleep( Main.opt.delay )

            start = time.time()
            testFile.seek( int( offset ) * 1024 )
            testFile.read( Main.opt.size )
            end = time.time()
            timming.append( end - start )

        start = time.time()
        testFile.close()
        end = time.time()
        timming.append( end - start )

        for atime in timming:
            print "{0} ".format( int( atime * 1000000 ) ),
        print

Main.mainMethod()
