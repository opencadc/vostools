#!/usr/bin/env python

from vos.__version__ import version
try:
    from setuptools import setup, find_packages
except ImportError:
    try:
        from ez_setup import use_setuptools
        use_setuptools()
        from setuptools import setup, find_packages
    except ImportError :
        from distutils.core import setup

from distutils.core import Command


class PyTest(Command):
    user_options = []
    def initialize_options(self):
        pass
    def finalize_options(self):
        pass
    def run(self):
        import sys,subprocess
        errno = subprocess.call([sys.executable, 'runtests.py'])
        raise SystemExit(errno)

setup(name="vos",
      version=version,
      url="https://github.com/ijiraq/cadcVOFS",
      description="CADC VOSpace Filesystem",
      author="JJ Kavelaars",
      author_email="jj.kavelaars@nrc.gc.ca",
      packages=['vos'],
      scripts=['getCert','vsync','vcp','vrm','vls','vmkdir','mountvofs','vrmdir'],
      classifiers=[
        'Development Status :: 4 - Beta',
        'Environment :: Console',
        'Intended Audience :: Developers',
        'Intended Audience :: End Users/Desktop',
        'Intended Audience :: Science/Research',
        'License :: OSI Approved :: GNU Affero General Public License v3',
        'Operating System :: POSIX',
        'Programming Language :: Python',
        ],    
      cmdclass = {'test': PyTest}
)
