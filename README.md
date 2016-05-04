
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

## Development
A virtual environment (**venv**) is recommended to set up external dependencies without installing them system-wide. Following [these instructions](http://docs.python-guide.org/en/latest/dev/virtualenvs/), install **virtualenv**:
```
$ pip install virtualenv
```

Next, create, and activate a local **venv** (this example uses **bash**):
```
$ virtualenv venv
$ source venv/bin/activate

```

Finally, use **pip** to install missing external dependencies into this subdirectory:
```
$ pip install -r requirements.txt
```

After successfully installing the external dependencies, the unit tests are invoked by running
```
$ ant test
```

Each time you resume work on the project and want to use the **venv** (e.g., from a new shell), simply re-activate it:
```
$ source venv/bin/activate
```
When done, just issue a 
```
$ deactivate
```
command to deactivate the virtual environment.


### Integration Tests
The integration tests are, at present, designed to run only at CADC. Tests assume that scripts have been installed (e.g., into the **venv**). 
Note: the integration tests run only on tcsh.

Start the tcsh and activate the **venv**

```
$ source venv/bin/activate.csh
```

Set the python binary for testing. Before using **virtualenv** this was used to test on multiple version of python. With **venv**, just set it to the default version of python on that **venv**:

```
$ setenv CADC_PYTHON_TEST_TARGETS python
```

Set the path to the vos script locatio

```
$ setenv CACD_ROOT <vos directory>
```

Finally, add the development vos to the **PYTHONPATH**:

```
$ setenv PYTHONPATH $CADC_ROOT:$PYTHONPATH
```
CADC_ROOT

Now, it's time to run the test:

```
$ cd test/scripts
$ ./vospace-all.tcsh
```
