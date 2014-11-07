
# DOCUMENTATION

vos is a set of python modules and scripts that ease access to VOSpace.

The default installation of vos is tuned for accessing the VOSpace provided by the [Canadian Advanced Network For Astronomical Research](http://www.canfar.net/) (CANFAR)

VOSpace is a Distributed Cloud storage service for use in Astronomy.

There are three ways to use vos:
      
1. access VOSpace using the command-line script: eg. *vcp*
1. make VOSpace appear as mounted filesystem: *mountvofs*
1. use the vos module inside a Python script: `import vos`

Authentication to the CANFAR VOSpace service is performed using X509 security certificates, header tokens or username/password pairs.
The authentication system is managed by the CADC Group Management Service (GMS).

To retrieve an X509 security certificate for use with the `vos` tools use the *getCert* script included with this package.

Additional information is available in the [CANFAR documentation](http://www.canfar.net/docs/vospace/)

## System Requirments

* A CANFAR VOSpace account (required for WRITE access, READ access can be anonymous)
* fuse OR OSX-FUSE  (see additional documentation, only required for filesystem  based access, not for command line or programmatic)
* python2.6 or later

## Installation

vos is distributed via [PyPI/vos](pypi.python.org/pypi/vos) and PyPI is the most direct way to get the latest stable release:

`pip install vos --upgrade --user`

Or, you can retrieve the [github](github.com/canfar/vos) distribution and use

 `python setup.py install --user`


## Tutorial

1. Get a [CANFAR account](http://www.canfar.phys.uvic.ca/canfar/auth/request.html)
1. Install the vos package.
1. Retrieve a X509/SSL certificate using the built in `getCert` script.
1. Example Usage.
    1. For filesystem usage: `mountvofs`
  mounts the CADC VOSpace root Container Node at /tmp/vospace and
  initiates a 5GB cache in the users home directory (${HOME}/vos_).
  `fusermount -u /tmp/vospace` (LINUX) or `umount /tmp/vospace` (OS-X) unmounts the file system.
   *VOSpace does not have a mapping of your unix users
   IDs and thus files appear to be owned by the user who issued the
   'mountvofs' command.*
    1. Commandline usage:
        * `vls -l vos:`   [List a vospace]
        * `vcp vos:jkavelaars/test.txt ./`  [copies test.txt to the local directory from vospace]
        * `vmkdir --help` [get a list of command line options and arguments]
        * `vmkdir`, `vrm`, `vrmdir`, `vsync` `vcat`, `vchmod` and `vln`
    1. In a Python script (the example below provides a listing of a vospace container)
```
#!python
import vos
client = vos.Client()
client.listdir('vos:jkavelaars')
```
