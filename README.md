
# DOCUMENTATION

vos is a set of python modules and scripts to enable access VOSpace.

The default installation of vos is tuned for accessing the VOSpace provided by the [Canadian Advanced Network For Astronomical Research](http://www.canfar.net/) (CANFAR)

VOSpace is a Distributed Cloud storage service for use in Astronomy.

There are three ways to use cadcVOFS:
      
1. access VOSpace using the command-line script: eg. *vcp*
1. make VOSpace appear as mounted filesystem: *mountvofs*
1. use the vos module inside a Python script: `import vos`

All of these methods require the user to have a pre-existing VOSpace account.

Authentication to the CANFAR VOSpace service is performed using X509 security certificates that are managed by the CADC Group Management Service (GMS).

The certificates can be retrieved from the CADC using the *getCert* script included with this package.

## ADDITIONAL DOCUMENTATION AVAILABLE AT:

      <http://www.canfar.net>

# SYSTEM REQUIREMENTS

* A CANFAR VOSpace account
* fuse OR OS-FUSE  (see additional documentation)
* python2.6 or newer

# INSTALLATION

vos is distributed via PyPI and that is the most direct way to get the latest stable release:

`pip install vos --upgrade --user`

Or, you can retrieve the github distribution and use

 `python setup.py install --user`


# USAGE

1. Get a [CANFAR account](http://www.canfar.phys.uvic.ca/canfar/auth/request.html)
1. Install the vos package.
1. Retrieve a X509/SSL certificate using the built in `getCert` script.
1. Example Usage.
    1. For filesystem usage: `mountvofs`
  mounts the CADC VOSpace root Container Node at /tmp/vospace and
  initiates a 5GB cache in the users home directory (${HOME}/vos_).
  `fusermount -u /tmp/vospace` (LINUX) or `umount /tmp/vospace` (OS-X) unmounts the file system.
   * VOSpace does not have a mapping of your unix users
   IDs and thus files appear to be owned by the user who issued the
   'mountvofs' command.*
    1. Commandline usage:
        * `vls -l vos:`   [List a vospace]
        * `vcp vos:jkavelaars/test.txt ./`  [copy test.txt to the local directory from the vospace container jkavelaars]
        * See --help for the commands:  `vmkdir`, `vrm`, `vrmdir`, `vsync` `vcat` and `vln`
    1. In a program:
    'import vos
    client = vos.Client()
    client.listdir('vos:jkavelaars')'

    provides a listing of the vospace container 'jkavelaars'
