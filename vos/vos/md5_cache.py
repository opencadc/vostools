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
