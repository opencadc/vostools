#!/usr/bin/env python
# Licensed under a 3-clause BSD style license - see LICENSE.rst

import glob
import os
import sys
from setuptools.command.test import test as TestCommand
from setuptools import find_packages

from setuptools import setup


# read the README.rst file and return as string.
def readme():
    with open('README.rst') as r_obj:
        return r_obj.read()


# Get some values from the setup.cfg
try:
    from ConfigParser import ConfigParser
except ImportError:
    from configparser import ConfigParser

conf = ConfigParser()
conf.read(['setup.cfg'])
metadata = dict(conf.items('metadata'))

PACKAGENAME = metadata.get('package_name', 'packagename')
DESCRIPTION = metadata.get('description', 'CADC package')
AUTHOR = metadata.get('author', 'CADC')
AUTHOR_EMAIL = metadata.get('author_email', 'cadc@nrc.gc.ca')
LICENSE = metadata.get('license', 'unknown')
URL = metadata.get('url', 'http://www.cadc-ccda.hia-iha.nrc-cnrc.gc.ca')

# Get the long description from the package's README.rst
LONG_DESCRIPTION = readme()

# VERSION should be PEP386 compatible (http://www.python.org/dev/peps/pep-0386)
VERSION = metadata.get('version', 'none')

# generate the version file
with open(os.path.join(PACKAGENAME, 'version.py'), 'w') as f:
    f.write('version = \'{}\''.format(VERSION))

# Treat everything in scripts except README.rst as a script to be installed
scripts = [fname for fname in glob.glob(os.path.join('scripts', '*'))
           if os.path.basename(fname) != 'README.rst']

# Define entry points for command-line scripts
entry_points = {'console_scripts': []}

entry_point_list = conf.items('entry_points')
for entry_point in entry_point_list:
    entry_points['console_scripts'].append('{0} = {1}'.format(entry_point[0],
                                                              entry_point[1]))


# add the --cov option to the test command
class PyTest(TestCommand):
    """class py.test for the testing

    """
    user_options = []

    def __init__(self, dist, **kw):
        TestCommand.__init__(self, dist, **kw)
        self.pytest_args = ['--cov', PACKAGENAME]

    def run_tests(self):
        # import here, cause outside the eggs aren't loaded
        import pytest
        err_no = pytest.main(self.pytest_args)
        sys.exit(err_no)


# Note that requires and provides should not be included in the call to
# ``setup``, since these are now deprecated. See this link for more details:
# https://groups.google.com/forum/#!topic/astropy-dev/urYO8ckB2uM

setup(name=PACKAGENAME,
      version=VERSION,
      description=DESCRIPTION,
      scripts=scripts,
      install_requires=metadata.get('install_requires', '').strip().split(),
      author=AUTHOR,
      author_email=AUTHOR_EMAIL,
      license=LICENSE,
      url=URL,
      long_description=LONG_DESCRIPTION,
      zip_safe=False,
      use_2to3=False,
      setup_requires=['pytest-runner'],
      entry_points=entry_points,
      packages=find_packages(),
      package_data={PACKAGENAME: ['data/*', 'tests/data/*', '*/data/*', '*/tests/data/*']},
      cmdclass={'coverage': PyTest}
      )
