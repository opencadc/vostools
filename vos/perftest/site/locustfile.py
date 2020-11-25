import os
import sys
import time
import inspect
import logging
import random

from locust import User, events, task, constant_pacing
from uuid import uuid1
import vos
from vos.commands import vcp
from random import randrange
from testers import SiteTester, get_config

DEBUG = os.getenv('DEBUG', None)
ONE_KB = 1024
try:
    call_freq = int(get_config()['CALL_FREQ'])
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


class VCPClient(object):

  def __init__(self, resource_id, certfile):
    self.resource_id = resource_id
    self.certfile = certfile

  """
  Simple, sample XML RPC client implementation that wraps storage_inventory.Client and 
  fires locust events on request_success and request_failure, so that all requests 
  gets tracked in locust's statistics.
  """
  def copy(self, source, destination, name, size):
      _args = ['vcp']
      if DEBUG is not None:
          _args.append('-d')

      _args.extend(['--certfile={}'.format(self.certfile),
                    '--resource-id={}'.format(self.resource_id),
                    source, destination])
      sys.argv = _args

      start_time = time.time()
      try:
          vcp()
      except Exception as e:
          total_time = int((time.time() - start_time) * 1000)
          events.request_failure.fire(request_type='vcp', name='vcp:{}__{}'.format(source, destination), response_time=total_time, response_length=0, exception=e)
      else:
          total_time = int((time.time() - start_time) * 1000)
          events.request_success.fire(request_type='vcp', name=name, response_time=total_time, response_length=size)
          # In this example, we've hardcoded response_length=0. If we would want the response length to be 
          # reported correctly in the statistics, we would probably need to hook in at a lower level


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
            events.request_failure.fire(request_type="PUT+GET+DEL",
                                        name=func.__name__,
                                        response_time=total,
                                        response_length=0,
                                        exception=e)
        else:
            total = int((time.time() - start) * 1000)
            events.request_success.fire(request_type="PUT+GET+DEL",
                                        name=func.__name__,
                                        response_time=total,
                                        response_length=0)
        return result

    return wrapper

class Site(User):
    wait_time = constant_pacing(60/call_freq)

    def __init__(self, *args, **kwargs):
        super(Site, self).__init__(*args, **kwargs)
        self.site_tester = SiteTester()

    @task
    @stopwatch
    def cycle(self):
        file_size = self.site_tester.get_next_file_size()
        # URI domains for files to put into
        domains = ['cadc:TEST-CEPH4K-PUB', 'cadc:TEST-CEPH4K-PRIV']
        if get_config().get('ANON_TESTS_ONLY', None):
            domains.pop(1)
        if get_config().get('AUTH_TESTS_ONLY', None):
            domains.pop(0)
        if not domains:
            raise AttributeError('Cannot set both ANON_TESTS_ONLY and '
                                 'AUTH_TESTS_ONLY in config')
        start = time.time()
        uri_domain = random.choice(domains)
        if 'PRIV' in uri_domain:
            file_type = 'priv-{}B'.format(file_size)
        else:
            file_type = 'pub-{}B'.format(file_size)
        try:
            file_uri = self.site_tester.put_file(uri_domain, file_size)
        except Exception as e:
            total = int((time.time() - start) * 1000)
            events.request_failure.fire(request_type='PUT',
                                        name=file_type,
                                        response_time=total,
                                        response_length=file_size,
                                        exception=e)

        total = int((time.time() - start) * 1000)
        events.request_success.fire(request_type='PUT',
                                    name=file_type,
                                    response_time=total,
                                    response_length=file_size)

        start = time.time()
        try:
            self.site_tester.get_file(file_uri)
        except Exception as e:
            total = int((time.time() - start) * 1000)
            events.request_failure.fire(request_type='GET',
                                        name=file_type,
                                        response_time=total,
                                        response_length=file_size,
                                        exception=e)

        total = int((time.time() - start) * 1000)
        events.request_success.fire(request_type='GET',
                                    name=file_type,
                                    response_time=total,
                                    response_length=file_size)

        try:
            self.site_tester.delete_file(file_uri)
        except Exception as e:
            total = int((time.time() - start) * 1000)
            events.request_failure.fire(request_type='DELETE',
                                        name=file_type,
                                        response_time=total,
                                        response_length=0,
                                        exception=e)

        total = int((time.time() - start) * 1000)
        events.request_success.fire(request_type='DELETE',
                                    name=file_type,
                                    response_time=total,
                                    response_length=0)

# class ApiUser(VCPLocust):
#   wait_time = between(0.1, 1)
#   source = ''
#   size = int(os.getenv('FILE_SIZE_IN_BYTES', ONE_KB))
#
#   class task_set(TaskSet):
#     def setup(self):
#       file_dir = os.getenv('FILE_DIR', '/tmp')
#       ApiUser.source = _touch('{}/SOURCE_FILE.txt'.format(file_dir), ApiUser.size)
#
#     def teardown(self):
#       os.remove(ApiUser.source)
#
#     @task
#     def upload(self):
#       file_id = uuid1()
#       dest = 'cadc:CADCRegtest1/{}.txt'.format(file_id)
#       self.client.copy(ApiUser.source, dest, 'upload_{}'.format(ApiUser.size), ApiUser.size)

