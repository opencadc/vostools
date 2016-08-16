import sys
import os

if sys.version_info[0] > 2:
    print "The vos package is only compatible with Python version 2.n"
    sys.exit(-1)

# Build the list of scripts to be installed.

dependencies = ['requests >= 2.8']

script_dir = 'scripts'
scripts = []
for script in os.listdir(script_dir):
    if script[-1] in ["~", "#"]:
        continue
    scripts.append(os.path.join(script_dir,script))

try:
    from setuptools import setup, find_packages
    has_setuptools = True
except:
    from distutils.core import setup
    has_setuptools = False


from vos import __version__

setup(name="vos",
      version=__version__.version,
      url="https://github.com/ijiraq/cadcVOFS",
      description="Tools for interacting with CADC VOSpace.",
      author="JJ Kavelaars, Norm Hill, Adrian Demain, Ed Chapin and others",
      maintainer="JJ Kavelaars",
      maintainer_email="jj.kavelaars@nrc-cnrc.gc.ca",
      long_description="""a module and scripts designed for accessing IVAO VOSpace 2.0 compatible services""",
      license="AGPLv3",
      packages=find_packages(exclude=['test.*']),
      scripts=scripts,
      classifiers=[
          'Development Status :: 5 - Production/Stable',
          'Environment :: Console',
          'Intended Audience :: Developers',
          'Intended Audience :: End Users/Desktop',
          'Intended Audience :: Science/Research',
          'License :: OSI Approved :: GNU Affero General Public License v3',
          'Operating System :: POSIX',
          'Programming Language :: Python',
      ],
      install_requires=dependencies
      )




