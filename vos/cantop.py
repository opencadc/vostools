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
            
      self.main_window = curses.initscr()
      curses.noecho()
      curses.cbreak()
      self.keep_columns=['Job_ID',
                         'User',
                         'Started_on',
                         'Status',
                         'VM_Type',
                         'Command']
      self.filter = {'User': None,
                     'Status': None}

   def set_filter(self, ch):
      """Set a filter value"""
      
      columns = {ord('u'): 'User',
                 ord('s'): 'Status'}
      if ch in columns:
         self.filter[columns[ch]] = self.get_value(columns[ch])

      if ch == ord('a'):
         for key in self.filter:
            self.filter[key] = None

   def get_status(self):
      f=StringIO(c.open(uri=None,URL='https://www.canfar.phys.uvic.ca/proc/pub').read())

      

      with warnings.catch_warnings():
         warnings.simplefilter("ignore")
         table=votable.parse(f, invalid='mask').get_first_table().to_table()

      resp = "%s \n" % ( datetime.now() ) 

      for key in self.filter:
         if self.filter[key] is None:
            continue
         table = table[table[key]==self.filter[key]]
         resp += "%s: %s\t" % (key,self.filter[key]) 
      table.keep_columns(self.keep_columns)
      table = table[tuple(self.keep_columns)]
      resp += "\n"
      resp += str(table)
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
            elapsed += RATE
	 if cmd == ord('q'):
	    break
   finally:
      curses.nocbreak()
      curses.echo()
      curses.endwin()


