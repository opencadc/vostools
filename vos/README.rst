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
use the *cadc-get-cert* script included with this package.

Additional information is available in the `CANFAR
documentation <http://www.canfar.net/docs/vospace/>`__

System Requirments
------------------

-  A CANFAR VOSpace account (required for WRITE access, READ access can
   be anonymous)
-  python3.7 or later

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
3. Retrieve a X509/SSL certificate using the ``cadc-get-cert``
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


Integration Tests
~~~~~~~~~~~~~~~~~

The integration tests are, at present, designed to run only with the
CADC VOSpace and test accounts credentials. Tests assume that vos and/or vofs packages have been
installed.

Run the tests:

    $ ./test/scripts/vospace-all.tcsh
