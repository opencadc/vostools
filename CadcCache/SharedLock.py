import threading
import time

class TimeoutError(Exception):
    def __init__(self, value):
	self.value = value
    def __str__(self):
	return repr(self.value)

class RecursionError(Exception):
    def __init__(self, value):
	self.value = value
    def __str__(self):
	return repr(self.value)

class SharedLock(object):

    def __init__(self):
	"""Constructor for a shared lock.
	"""
	self.lock = threading.RLock()
	self.condition = threading.Condition(self.lock)
	self.exclusiveLock = None
	self.lockersList = set()

    def acquire(self, timeout = None, shared = True):
	"""Acquire a lock.
	"""
	if timeout is not None:
	    waitStart = time.time()
	    waitTime = 0

	with self.lock:
	    # Wait until there are no exclusive locks
	    while self.exclusiveLock is not None:
		if timeout is None:
		    # No timeouts
		    self.condition.wait()
		else:
		    self.condition.wait(timeout - waitTime)
		    waitTime = time.time() - waitStart
		    if self.exclusiveLock is not None and waitTime > timeout:
			raise TimeoutError("Timout waiting for a shared lock")

	    # exclusive lock is now None, we can acquire either a shared or
	    # exclusive lock
	    if shared:
		if threading.current_thread() in self.lockersList:
		    raise RecursionError( "SharedLock is not recursive" )
		self.lockersList.add(threading.current_thread())
	    else:
		while len(self.lockersList) > 0:
		    if timeout is None:
			# No timeouts
			self.condition.wait()
		    else:
			self.condition.wait(timeout - waitTime)
			waitTime = time.time() - waitStart
			if len(self.lockersList) > 0 and waitTime > timeout:
			    raise TimeoutError(
				    "Timout waiting for a shared lock")
		self.exclusiveLock = threading.current_thread()

    def release(self):
	with self.lock:
	    if self.exclusiveLock == threading.current_thread():
		self.exclusiveLock = None
		assert len(self.lockersList) == 0
	    else:
		self.lockersList.remove(threading.current_thread())



