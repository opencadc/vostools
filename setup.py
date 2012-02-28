#!/usr/bin/python

try:
    from setuptools import setup, find_packages
except ImportError:
    from ez_setup import use_setuptools
    use_setuptools()
    from setuptools import setup, find_packages

setup(name="vofs",
      version="1.1",
      url="http://www.cadc.hia.nrc.gc.ca/vosui/#jkavelaars/",
      description="CADC VOSpace Filesystem",
      author="JJ Kavelaars",
      author_email="jj.kavelaars@nrc.gc.ca",
      py_modules=['fuse','vos'],
      scripts=['getCert','vsync','vcp','vrm','vls','vmkdir','mountvofs','vrmdir']
)
