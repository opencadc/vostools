
import sqlite3
READBUF = 8192

class MD5_Cache:

   def __init__(self, cache_db="/tmp/#vos_cached.db#"):	
      """Setup the sqlDB that will contain the cache table"""
      self.cache_db=cache_db

      ## initialize the md5Cache db
      sqlConn = sqlite3.connect(self.cache_db)
      sqlConn.execute("create table if not exists md5_cache (fname text, md5 text, st_mtime int)")
      sqlConn.commit()
      sqlConn.close()
      ## build cache lookup if doesn't already exists

   def get(self,fname):
      """Get the MD5 for this fname from the SQL cache"""
      sqlConn=sqlite3.connect(self.cache_db)
      sqlConn.row_factory = sqlite3.Row
      cursor= sqlConn.cursor()
      cursor.execute("SELECT * FROM md5_cache WHERE fname = ?", (fname,))
      md5Row=cursor.fetchone()
      cursor.close()
      sqlConn.close()
      return md5Row
   
   def delete(self,fname):
      """Delete a record from the cache MD5 database"""
      sqlConn=sqlite3.connect(self.cache_db)
      sqlConn.row_factory =  sqlite3.Row
      cursor = sqlConn.cursor()
      cursor.execute("DELETE from md5_cache WHERE fname = ?", ( fname,))
      sqlConn.commit()
      cursor.close()
      sqlConn.close()
      return
   
   
   def update(self,fname):
      """Update a record in the cache MD5 database"""
      import hashlib, os
      md5=hashlib.md5()
      r=open(fname,'r')
      while True:
         buf=r.read(READBUF)
         if len(buf)==0:
            break
         md5.update(buf)
      r.close()
         
      ## UPDATE the MD5 database
      sqlConn=sqlite3.connect(self.cache_db)
      sqlConn.row_factory = sqlite3.Row
      cursor=sqlConn.cursor()
      cursor.execute("DELETE FROM md5_cache WHERE fname = ?", (fname,))
      if md5 is not None:
         cursor.execute("INSERT INTO md5_cache (fname, md5, st_mtime) VALUES ( ?, ?, ?)", (fname, md5.hexdigest(), os.stat(fname).st_mtime))
      sqlConn.commit()
      cursor.close()
      sqlConn.close()
      return

