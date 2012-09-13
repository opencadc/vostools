#/*+
#************************************************************************
#****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
#*
#* (c) 2012.                            (c)2012.
#* National Research Council            Conseil national de recherches
#* Ottawa, Canada, K1A 0R6              Ottawa, Canada, K1A 0R6
#* All rights reserved                  Tous droits reserves
#*
#* NRC disclaims any warranties,        Le CNRC denie toute garantie
#* expressed, implied, or statu-        enoncee, implicite ou legale,
#* tory, of any kind with respect       de quelque nature que se soit,
#* to the software, including           concernant le logiciel, y com-
#* without limitation any war-          pris sans restriction toute
#* ranty of merchantability or          garantie de valeur marchande
#* fitness for a particular pur-        ou de pertinence pour un usage
#* pose.  NRC shall not be liable       particulier.  Le CNRC ne
#* in any event for any damages,        pourra en aucun cas etre tenu
#* whether direct or indirect,          responsable de tout dommage,
#* special or general, consequen-       direct ou indirect, particul-
#* tial or incidental, arising          ier ou general, accessoire ou
#* from the use of the software.        fortuit, resultant de l'utili-
#*                                      sation du logiciel.
#*
#************************************************************************
#*
#*   Script Name:       setup.py
#*
#*   Purpose:
#*      Distutils setup script for caom2
#*
#*   Functions:
#*
#*
#*
#****  C A N A D I A N   A S T R O N O M Y   D A T A   C E N T R E  *****
#************************************************************************
#-*/

# Use "distribute"
import distribute_setup
distribute_setup.use_setuptools()

from setuptools import setup, find_packages
import sys

if sys.version_info[0] > 2:
    print 'The caom2 package is only compatible with Python version 2.n'
    sys.exit(-1)


#from distutils.core import Command


#class PyTest(Command):
#    user_options = []
#    def initialize_options(self):
#        pass
#    def finalize_options(self):
#        pass
#    def run(self):
#        import sys,subprocess
#        errno = subprocess.call([sys.executable, 'runtests.py'])
#        raise SystemExit(errno)

from vos.__version__ import version
setup(name="vos",
      version=version,
      url="https://github.com/ijiraq/cadcVOFS",
      description="CADC VOSpace Filesystem",
      author="JJ Kavelaars",
      author_email="jj.kavelaars@nrc.gc.ca",
      packages=['vos'],
      scripts=['getCert','vsync','vmv','vcp','vrm','vls','vmkdir','mountvofs','vrmdir', 'vln', 'vcat', 'vtag' ],
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
#      cmdclass = {'test': PyTest}
)
