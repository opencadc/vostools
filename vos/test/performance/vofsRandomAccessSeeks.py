# ***********************************************************************
# ******************  CANADIAN ASTRONOMY DATA CENTRE  *******************
# *************  CENTRE CANADIEN DE DONNÉES ASTRONOMIQUES  **************
#
#  (c) 2022.                            (c) 2022.
#  Government of Canada                 Gouvernement du Canada
#  National Research Council            Conseil national de recherches
#  Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
#  All rights reserved                  Tous droits réservés
#
#  NRC disclaims any warranties,        Le CNRC dénie toute garantie
#  expressed, implied, or               énoncée, implicite ou légale,
#  statutory, of any kind with          de quelque nature que ce
#  respect to the software,             soit, concernant le logiciel,
#  including without limitation         y compris sans restriction
#  any warranty of merchantability      toute garantie de valeur
#  or fitness for a particular          marchande ou de pertinence
#  purpose. NRC shall not be            pour un usage particulier.
#  liable in any event for any          Le CNRC ne pourra en aucun cas
#  damages, whether direct or           être tenu responsable de tout
#  indirect, special or general,        dommage, direct ou indirect,
#  consequential or incidental,         particulier ou général,
#  arising from the use of the          accessoire ou fortuit, résultant
#  software.  Neither the name          de l'utilisation du logiciel. Ni
#  of the National Research             le nom du Conseil National de
#  Council of Canada nor the            Recherches du Canada ni les noms
#  names of its contributors may        de ses  participants ne peuvent
#  be used to endorse or promote        être utilisés pour approuver ou
#  products derived from this           promouvoir les produits dérivés
#  software without specific prior      de ce logiciel sans autorisation
#  written permission.                  préalable et particulière
#                                       par écrit.
#
#  This file is part of the             Ce fichier fait partie du projet
#  OpenCADC project.                    OpenCADC.
#
#  OpenCADC is free software:           OpenCADC est un logiciel libre ;
#  you can redistribute it and/or       vous pouvez le redistribuer ou le
#  modify it under the terms of         modifier suivant les termes de
#  the GNU Affero General Public        la “GNU Affero General Public
#  License as published by the          License” telle que publiée
#  Free Software Foundation,            par la Free Software Foundation
#  either version 3 of the              : soit la version 3 de cette
#  License, or (at your option)         licence, soit (à votre gré)
#  any later version.                   toute version ultérieure.
#
#  OpenCADC is distributed in the       OpenCADC est distribué
#  hope that it will be useful,         dans l’espoir qu’il vous
#  but WITHOUT ANY WARRANTY;            sera utile, mais SANS AUCUNE
#  without even the implied             GARANTIE : sans même la garantie
#  warranty of MERCHANTABILITY          implicite de COMMERCIALISABILITÉ
#  or FITNESS FOR A PARTICULAR          ni d’ADÉQUATION À UN OBJECTIF
#  PURPOSE.  See the GNU Affero         PARTICULIER. Consultez la Licence
#  General Public License for           Générale Publique GNU Affero
#  more details.                        pour plus de détails.
#
#  You should have received             Vous devriez avoir reçu une
#  a copy of the GNU Affero             copie de la Licence Générale
#  General Public License along         Publique GNU Affero avec
#  with OpenCADC.  If not, see          OpenCADC ; si ce n’est
#  <http://www.gnu.org/licenses/>.      pas le cas, consultez :
#                                       <http://www.gnu.org/licenses/>.
#
#  $Revision: 4 $
#
# ***********************************************************************
#


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
            print("ERROR: Filename must be provided.")
            op.print_help()
            exit( -1 )

        if ( Main.opt.random != None ):
            # Get the file size
            statinfo = os.stat(Main.opt.fileName)
            maxValue = ( statinfo.st_size  / 1024 ) - Main.opt.size
            Main.opt.offsets = " ".join( str(w) for w in \
                    random.sample( range(maxValue), Main.opt.random) )
            print(Main.opt.offsets)
        else:
            for offset in Main.opt.offsets.split():
                if ( not offset.isdigit() ):
                    print("ERROR: Offsets must be integers")
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
            print("{0} ".format( int( atime * 1000000 ) )),
        print('')

Main.mainMethod()
