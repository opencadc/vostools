
# Use "distribute"
from setuptools import setup, find_packages
import sys

if sys.version_info[0] > 2:
    print 'The caom2 package is only compatible with Python version 2.n'
    sys.exit(-1)


from vos.__version__ import version
setup(name="vos",
      version=version,
      url="https://github.com/ijiraq/cadcVOFS",
      description="Tools for interacting with CADC VOSpace.",
      author="JJ Kavelaars",
      author_email="jj.kavelaars@nrc.gc.ca",
      packages=['vos'],
      install_requires=['distribute'],
      scripts=['scripts/getCert','scripts/vsync','scripts/vmv','scripts/vcp','scripts/vrm','scripts/vls','scripts/vmkdir','scripts/mountvofs','scripts/vrmdir', 'scripts/vln', 'scripts/vcat', 'scripts/vtag', 'scripts/vchmod', 'scripts/checkJobPhase', 'scripts/vlock', 'cantop' ],
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
