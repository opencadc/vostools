
+++++++++++++++++++++++++ DOCUMENTATION ++++++++++++++++++++++++++

vos is a set of python modules and scripts to enable access VOSpace.

The default installation of vos is tuned for accessing the VOSpace provided by the Canadian Advanced Network For Astronomical Research http://www.canfar.net/

VOSpace is a Distributed Cloud storage service for use in Astronomy.

There are three ways to use cadcVOFS:
      
     1) access VOSpace from the command-line 

     2) make VOSpace appear as mounted filesystem
     
     3) use the vos module inside a Python script

All of these methods require the user to have a pre-existing VOSpace
account with the CADC.

Authentication to the CADC VOSpace service is performed using security
certificates which are managed by the CADC Group Management Service
(GMS).  The certificates can be retrieved from the CADC using the
'getCert' script and users CADC account and password information.

ADDITIONAL DOCUMENTATION AVAILABLE AT:  

      <http://www.canfar.phys.uvic.ca/wiki/index.php/VOSpace_filesystem>

+++++++++++++++++++++++ REQUIREMENTS +++++++++++++++++++++++++++++++

    A CADC VOSpace account
    fuse OR OS-FUSE  (see additional documentation)
    python2.6 or newer

+++++++++++++++++++++++++ INSTALLATION +++++++++++++++++++++++++++

python setup.py install 

+++++++++++++++++++++++++ USAGE ++++++++++++++++++++++++++++++++++

1) Get a CADC account <http://www.cadc.hia.nrc.gc.ca/>

2) Retrieve a SSL certificate from the CADC servers.

> getCert

  retrieves a certificate from the CADC servers, these certifcates are
  valid for 10 days (be default) but can be valid for upto 30 days (see
  the getCert help).  The cerificate is stored in ${HOME}/.ssl by
  default.

3a) For filesystem usage: 
Depending on your operating system you may be required to be in a sytem user
group in order to mount FUSE file systems.

> mountvofs

  mounts the CADC VOSpace root Container Node at /tmp/vospace and
  initiates a 5GB cache in the users home directory (${HOME}/vos_)

> fusermount -u /tmp/vospace
  unmounts the file system.

  **** Since the VOSpace does not have a mapping of your unix users
       IDs all files appear to be owned by the user who issued the
       'mountvofs' command.  Only that user can see the mount point.

3b) Commandline usage:

> vls -l vos:

  Lists the contents of the CADC root VOSpace Container Node.

> vcp vos:jkavelaars/test.txt ./

  copys the files 'test.txt' from the the vospace container jkavelaars
  to your local directory.

See --help for the commands:  vmkdir, vrm, vrmdir, vsync 

+++++++++++++++++++++++++ TEST SCRIPTS +++++++++++++++++++++++++++

The top-level "runtest" executes unit tests in vos/test

Integration and performance scripts are located in the test/ tree. At
present these are designed to test installations at the CADC and will
not work elsewhere. In order to run them, cadcVOFS must be installed
in the following way:

1) Set the environment variable $CADC_ROOT to the path where CADC
software are installed.

2) Install cadcVOFS
> python setup.py install --prefix=$CADC_ROOT \
  --install-script=$CADC_ROOT/scripts
