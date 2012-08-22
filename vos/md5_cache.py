
import sqlite3
READBUF = 8192

class MD5_Cache:

   def __init__(self, cache_db="/tmp/#vos_cached.db#"):	
      """Setup the sqlDB that will contain the cache table"""
      self.cache_db=cache_db

      ## initialize the md5Cache db
      sqlConn = sqlite3.connect(self.cache_db)
      with sqlConn:
         sqlConn.execute("create table if not exists md5_cache (fname text PRIMARY KEY NOT NULL , md5 text, st_mtime int)")
      sqlConn.close()
      ## build cache lookup if doesn't already exists

   def get(self,fname):
      """Get the MD5 for this fname from the SQL cache"""
      sqlConn=sqlite3.connect(self.cache_db)
      with sqlConn:
          cursor=sqlConn.execute("SELECT md5,st_mtime FROM md5_cache WHERE fname = ?", (fname,))
          md5Row=cursor.fetchone()
      sqlConn.close()
      if md5Row is not None: 
	return md5Row[0],md5Row[1]
      else:	
        return None
   
   def delete(self,fname):
      """Delete a record from the cache MD5 database"""
      sqlConn=sqlite3.connect(self.cache_db)
      with sqlConn:
           sqlConn.execute("DELETE from md5_cache WHERE fname = ?", ( fname,))
      sqlConn.close()
   
   
   def update(self,fname,md5):
      """Update a record in the cache MD5 database"""
      import os 
      ## UPDATE the MD5 database
      sqlConn=sqlite3.connect(self.cache_db)
      with sqlConn:
         sqlConn.execute("DELETE from md5_cache WHERE fname = ?", ( fname,))
         sqlConn.execute("INSERT INTO md5_cache (fname, md5, st_mtime) VALUES ( ?, ?, ?)", (fname, md5, os.stat(fname).st_mtime ))
      sqlConn.close()

