
from distutils.core import setup
import sys
import os
from vos.__version__ import version

if sys.version_info[0] > 2:
    print 'The caom2 package is only compatible with Python version 2.n'
    sys.exit(-1)

## Build the list of scripts to be installed.
script_dir = 'scripts'
scripts = []
for script in os.listdir(script_dir):
    if script[-1] in [ "~", "#"]:
        continue
    scripts.append(os.path.join(script_dir,script))


setup(name="vos",
      version=version,
      url="https://github.com/ijiraq/cadcVOFS",
      description="Tools for interacting with CADC VOSpace.",
      author="JJ Kavelaars, Norm Hill, Adrian Demain, Ed Chapin and others",
      author_email="jj.kavelaars@nrc-cnrc.gc.ca",
      packages=['vos'],
      scripts=scripts,
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
