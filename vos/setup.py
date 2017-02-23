import sys
import os

if sys.version_info[0] > 2:
    print "The vos package is only compatible with Python version 2.n"
    sys.exit(-1)

# Build the list of scripts to be installed.

# dependencies = [ x.strip() for x in open('requirements.txt').readlines()]
install_requires = ['requests>=2.8,<3.0',
                    'BitVector>=3.4.4,<4.0',
                    'html2text>=2016.5.29',
                    'fusepy>=2.0.4']

tests_require = ['pytest>=2.9.2',
                 'pytest-cov>=2.3.1',
                 'future>=0.15.2',
                 'unittest2>=1.1.0',
                 'magicmock>=0.3',
                 'mock>=2.0.0']

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


execfile('vos/__version__.py')

setup(name="vos",
      version=version,
      url="https://github.com/ijiraq/cadcVOFS",
      description="Tools for interacting with CADC VOSpace.",
      author="JJ Kavelaars, Norm Hill, Adrian Damian, Ed Chapin and others",
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
      setup_requires = ['pytest-runner'],
      install_requires=install_requires,
      tests_require=tests_require,
      )




