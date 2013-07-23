#!/usr/bin/env python2.7

import vos
from astropy.io import votable
from cStringIO import StringIO

import curses, time
from datetime import datetime
import warnings

c=vos.Client()

DELAY = 8
RATE = 1
PROGRESS = '*'

class Cantop(object):
      
   def __init__(self):
            

      self.keep_columns=['Job_ID',
                         'User',
                         'Started_on',
                         'Status',
                         'VM_Type',
                         'Command']
      self.filter = {'User': None,
                     'Status': None}

   def window_init(self):
      self.main_window = curses.initscr()
      curses.noecho()
      curses.cbreak()


   def set_filter(self, ch):
      """Set a filter value"""
      
      columns = {ord('u'): 'User',
                 ord('s'): 'Status'}
      if ch in columns:
         self.filter[columns[ch]] = self.get_value(columns[ch])

      if ch == ord('a'):
         for key in self.filter:
            self.filter[key] = None

   def get_proc_table(self):
      f=StringIO(c.open(uri=None,URL='https://www.canfar.phys.uvic.ca/proc/pub').read())

      with warnings.catch_warnings():
         warnings.simplefilter("ignore")
         return votable.parse(f, invalid='mask').get_first_table().to_table()

   def get_status(self):

      self.table = self.get_proc_table()

      resp = "%s \n" % ( str(datetime.now())[0:19] ) 

      for key in self.filter:
         if self.filter[key] is None:
            continue
         self.table = self.table[self.table[key]==self.filter[key]]
         resp += "%s: %s\t" % (key,self.filter[key]) 
      self.table.keep_columns(self.keep_columns)
      self.table = self.table[tuple(self.keep_columns)]
      self.table.sort('Job_ID')
      resp += "\n"
      resp += str(self.table)
      resp += "\n"

      return resp


   def get_value(self, prompt):

      curses.echo()
      self.main_window.addstr(1,0,prompt+" ")
      value = self.main_window.getstr()
      curses.noecho()
      if len(value) == 0:
         value = None
      return value

   def redraw(self):

      self.main_window.erase()
      self.main_window.addstr(self.get_status())
      self.main_window.refresh()
      
if __name__=='__main__':

   cantop = Cantop()

   try:
      while True:
         cantop.redraw()
         curses.halfdelay(RATE*10)
         elapsed = 0
         while elapsed < DELAY:
            cmd = cantop.main_window.getch()
            if cmd > 0:
               cantop.set_filter(cmd)
               break
            cantop.main_window.addch(1,25,str(DELAY-elapsed))
            cantop.main_window.refresh()
            elapsed += RATE
	 if cmd == ord('q'):
	    break
   finally:
      curses.nocbreak()
      curses.echo()
      curses.endwin()


