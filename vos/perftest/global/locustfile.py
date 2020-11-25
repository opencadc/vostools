import os
import sys
import time
import inspect
import logging

from locust import User, events, task, constant_pacing
from uuid import uuid1
import vos
from vos.commands import vcp
from random import randrange
from testers import GlobalTester, config

DEBUG = os.getenv('DEBUG', None)
ONE_KB = 1024
try:
    call_freq = int(config['CALL_FREQ'])
except KeyError:
    call_freq = 60
logger = logging.getLogger('locust.main')
logger.info('Running with {} calls/s'.format(call_freq))
# TODO Don't know how to do it with a logger
print('Running with {} calls/s'.format(call_freq))


def _touch(path, size=ONE_KB):
  with open(path, 'a') as f:
      f.truncate(size)
      f.close()
  return path

def stopwatch(func):
    """
    Wrapper to report a function execution time to locust
    :param func:
    :return:
    """
    def wrapper(*args, **kwargs):
        # get task's function name
        previous_frame = inspect.currentframe().f_back
        _, _, task_name, _, _ = inspect.getframeinfo(previous_frame)

        start = time.time()
        result = None
        try:
            result = func(*args, **kwargs)
        except Exception as e:
            total = int((time.time() - start) * 1000)
            events.request_failure.fire(request_type="TYPE",
                                        name=func.__name__,
                                        response_time=total,
                                        response_length=0,
                                        exception=e)
        else:
            total = int((time.time() - start) * 1000)
            events.request_success.fire(request_type="TYPE",
                                        name=func.__name__,
                                        response_time=total,
                                        response_length=0)
        return result

    return wrapper

class Global(User):
    wait_time = constant_pacing(60/call_freq)

    def __init__(self, *args, **kwargs):
        super(Global, self).__init__(*args, **kwargs)
        self.global_tester = GlobalTester()

    @task
    @stopwatch
    def anon_get_pub(self):
        """
        Negotiate a transfer request for a random public file
        :return:
        """
        self.global_tester.anon_get_pub()

    @task
    @stopwatch
    def anon_get_priv(self):
        """
        Unauthorized failures
        :return:
        """
        self.global_tester.anon_get_priv()

    @task
    @stopwatch
    def auth_get_pub(self):
        """
        Negotiate a transfer request for a random public file
        :return:
        """
        self.global_tester.auth_get_pub()

    @task
    @stopwatch
    def auth_get_priv(self):
        self.global_tester.auth_get_priv()

    @task
    @stopwatch
    def anon_put(self):
        """
        Negotiate a transfer request for pushing a file anon - Failure
        :return:
        """
        self.global_tester.anon_put()

    @task
    @stopwatch
    def auth_put(self):
        """
        Negotiate a transfer request for pushing a file
        :return:
        """
        self.global_tester.auth_put()


