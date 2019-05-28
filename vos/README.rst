DOCUMENTATION
=============

.. image:: https://img.shields.io/pypi/v/vos.svg   
    :target: https://pypi.python.org/pypi/vos

vos is a set of python modules and scripts that ease access to VOSpace.

The default installation of vos is tuned for accessing the VOSpace
provided by the `Canadian Advanced Network For Astronomical
Research <http://www.canfar.net/>`__ (CANFAR)

VOSpace is a Distributed Cloud storage service for use in Astronomy.

There are two ways to use vos:

1. access VOSpace using the command-line script: eg. *vcp*
2. use the vos module inside a Python script: ``import vos``

Authentication to the CANFAR VOSpace service is performed using X509
security certificates, header tokens or username/password pairs. The
authentication system is managed by the CADC Group Management Service
(GMS).

To retrieve an X509 security certificate for use with the ``vos`` tools
use the *getCert* script included with this package.

Additional information is available in the `CANFAR
documentation <http://www.canfar.net/docs/vospace/>`__

System Requirments
------------------

-  A CANFAR VOSpace account (required for WRITE access, READ access can
   be anonymous)
-  fuse OR OSX-FUSE (see additional documentation, only required for
   filesystem based access, not for command line or programmatic)
-  python2.6 or later

Installation
------------

vos is distributed via `PyPI/vos <pypi.python.org/pypi/vos>`__ and PyPI
is the most direct way to get the latest stable release:

``pip install vos --upgrade --user``

Or, you can retrieve the `github <github.com/canfar/vos>`__ distribution
and use

``python setup.py install --user``

Tutorial
--------

1. Get a `CANFAR
   account <http://www.canfar.phys.uvic.ca/canfar/auth/request.html>`__
2. Install the vos package.
3. Retrieve a X509/SSL certificate using the ``getCert``
   script installed as part of the ``cadcutils`` package that is automatically installed wiht vos.
4. Example Usage.

   1. Commandline usage:

      -  ``vls -l vos:`` [List a vospace]
      -  ``vcp vos:jkavelaars/test.txt ./`` [copies test.txt to the
         local directory from vospace]
      -  ``vchmod g+q vos:VOSPACE/foo/bar.txt 'GROUP1, GROUP2, GROUP3'`` to give three user GROUPs permission
         to write to this file.
      -  ``vmkdir --help`` [get a list of command line options and
         arguments]
      -  ``vmkdir``, ``vrm``, ``vrmdir``, ``vsync`` ``vcat``, ``vchmod``
         and ``vln``
      -  The complete list of ``vos`` commmand line tools can be found using ``pydoc vos.commands``

   2. In a Python script (the example below provides a listing of a
      vospace container)

      ::

          #!python
          import vos
          client = vos.Client()
          client.listdir('vos:jkavelaars')

Development
-----------

A virtual environment (**venv**) is recommended to set up external
dependencies without installing them system-wide. Following `these
instructions <http://docs.python-guide.org/en/latest/dev/virtualenvs/>`__,
install **virtualenv**:

::

    $ pip install virtualenv

Next, create, and activate a local **venv** (this example uses
**bash**):

::

    $ virtualenv venv
    $ source venv/bin/activate


Setup the new development environment for testing by installing the appropriate packages:

::

    $ pip install -r dev_requirements.txt

The test environment is built into the *setup.py* so that conducting unit-tests can be achieved like so:

::

    $ python setup.py test

If you would like versbose output formated as a web page, for example,
you can add options to the test call:

::

    $ python setup.py test --addopts '--cov-report html:cov_html --cov=vos'

The same option attribute can be used to pass other arguments to py.test
that is executing the test. To run specific only tests for example:

::

    $ python setup.py test --addopts 'vos/test/Test_vos.py::TestClient::test_transfer_error'

Each time you resume work on the project and want to use the **venv**
(e.g., from a new shell), simply re-activate it:

::

    $ source venv/bin/activate

When done, just issue a

::

    $ deactivate

command to deactivate the virtual environment.

Integration Tests
~~~~~~~~~~~~~~~~~

The integration tests are, at present, designed to run only with the
CADC VOSpace. Tests assume that vos and/or vofs packages have been
installed.

Activate the **venv** and install vos

::

    $ source venv/bin/activate.csh
    $ pip install vos

Run the tests:

    $ ./test/scripts/vospace-all.tcsh
