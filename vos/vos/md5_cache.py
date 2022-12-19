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

"""
 A cache of MD5 meta data associated with VOSpace transfers.

 When transferring large numbers of files to and from VOSpace there is a
 substantial expectation of network failures which will require that a
 transfer be re-attempted.  This module provides a class that keeps track
 of the MD5 values associated files on disk and in VOSpace allowing the
 caller to choose to skip files that match (MD5 wise) between the
 two locations.
"""
import sqlite3
import logging
import hashlib
import tempfile

READ_BUFFER_SIZE = 8192


class MD5Cache:
    def __init__(self, cache_db=None):
        """Setup the sqlDB that will contain the cache table.

        The slqDB can then be used to lookup MD5 values rather than
        recompute them at each restart of a transfer.

        :param cache_db: The path and filename where the SQL db will be stored.
        """
        if cache_db is None:
            self.cache_obj = tempfile.NamedTemporaryFile()
            self.cache_db = self.cache_obj.name
        else:
            self.cache_db = cache_db

        # initialize the md5Cache db
        sql_conn = sqlite3.connect(self.cache_db)
        with sql_conn:
            # build cache lookup if doesn't already exists
            sql_conn.execute(
                ("create table if not exists "
                 "md5_cache (filename text PRIMARY KEY NOT NULL , "
                 "md5 text, st_size int, st_mtime int)"))

    @staticmethod
    def compute_md5(filename, block_size=READ_BUFFER_SIZE):
        """
        A convenience routine that computes and returns the MD5 of the
        supplied filename.
        :param filename: Name of the file to compute the MD5 checksum for.
        :param block_size: Loop through the file with this number of
        bytes per call.
        :return: the MD5 hexdigest of the file.
        :rtype: str
        """
        md5 = hashlib.md5()
        with open(filename, 'rb') as f:
            while True:
                buf = f.read(block_size)
                if len(buf) == 0:
                    break
                md5.update(buf)
        return md5.hexdigest()

    def get(self, filename):
        """Get the MD5 for filename.

        First look in MD5 cache databse and then compute if needbe.

        :param filename: name of the file you want the MD5 sum for.
        """
        slq_conn = sqlite3.connect(self.cache_db)
        with slq_conn:
            cursor = slq_conn.execute(
                "SELECT md5, st_size, "
                "st_mtime FROM md5_cache WHERE filename = ? ",
                (filename,))
            md5_row = cursor.fetchone()
        if md5_row is not None:
            return md5_row
        else:
            return None

    def delete(self, filename):
        """Delete a record from the cache MD5 database.

        :param filename: Name of the file whose md5 record to be deleted
        from the cache database
        """
        sql_conn = sqlite3.connect(self.cache_db)
        with sql_conn:
            sql_conn.execute("DELETE from md5_cache WHERE filename = ?",
                             (filename,))

    def update(self, filename, md5, st_size, st_mtime):
        """Update the MD5 value stored in the cache db

        :param filename: Name of the file to update the MD5 value for.
        :param md5: the MD5 hexdigest to be stored to the databse.
        :param st_size: size of the file being updated (stored to database)
        :param st_mtime: last modified time of the file being stored to
        database.
        """

        # UPDATE the MD5 database
        sql_connection = sqlite3.connect(self.cache_db)
        try:
            with sql_connection:
                sql_connection.execute(
                    "DELETE from md5_cache WHERE filename = ?", (filename,))
                sql_connection.execute(
                    ("INSERT INTO md5_cache (filename, md5, st_size, st_mtime)"
                     " VALUES ( ?, ?, ?, ?)"),
                    (filename, md5, st_size, st_mtime))
        except Exception as e:
            logging.error(e)
        return md5
