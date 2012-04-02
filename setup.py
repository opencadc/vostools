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
)
