DOCUMENTATION
=============

.. image:: https://img.shields.io/pypi/v/vofs.svg   
    :target: https://pypi.python.org/pypi/vofs

vosfs is a python module that allows a VOSpace service to be used as a
file system.

The default installation of vosfs is tuned for accessing the VOSpace
provided by the `Canadian Advanced Network For Astronomical
Research <http://www.canfar.net/>`__ (CANFAR)

VOSpace is a Distributed Cloud storage service for use in Astronomy.

There are two ways to use vos:

1. make VOSpace appear as mounted filesystem: *mountvofs*
2. use the vosfs module inside a Python script: ``import vosfs``

Authentication to the CANFAR VOSpace service is performed using X509
security certificates, header tokens or username/password pairs. The
authentication system is managed by the CADC Group Management Service
(GMS).

To retrieve an X509 security certificate for use with the ``vosfs``
tools use the *cadc-get-cert* script included with the cadcutils
package.

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

vosfs is distributed via `PyPI/vos <pypi.python.org/pypi/vosfs>`__ and
PyPI is the most direct way to get the latest stable release:

``pip install vosfs --upgrade --user``

Or, you can retrieve the `github <github.com/canfar/vosfs>`__
distribution and use

``python setup.py install --user``

Tutorial
--------

1. Get a `CANFAR
   account <http://www.canfar.phys.uvic.ca/canfar/auth/request.html>`__
2. Install the vosfs package.
3. Retrieve a X509/SSL certificate using the installed in
   ``cadc-get-cert`` script.
4. Example Usage.

   1. For filesystem usage: ``mountvofs`` mounts the CADC VOSpace root
      Container Node at /tmp/vospace and initiates a 5GB cache in the
      users home directory (${HOME}/vos\_).
      ``fusermount -u /tmp/vospace`` (LINUX) or ``umount /tmp/vospace``
      (OS-X) unmounts the file system. *VOSpace does not have a mapping
      of your unix users IDs and thus files appear to be owned by the
      user who issued the 'mountvofs' command.*


Integration Tests
~~~~~~~~~~~~~~~~~

The integration tests are, at present, designed to run only with the
CADC VOSpace. Tests assume that the vofs package has been installed.

Run the tests:

\`\`\` $ ./test/scripts/vospace-mountvospace-atest.tcsh
