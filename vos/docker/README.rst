Docker builds
=============

Versions
~~~~~~~~

Supported Python versions are:

 - 2.7
 - 3.6
 - 3.7
 - 3.8


There are Dockerfiles for each version in the docker/${version} folders.

Integration Tests
~~~~~~~~~~~~~~~~~

Only 2.7 is supported for Integration Tests.


Building
~~~~~~~~

Docker builds rely on the `git` repository to build from.  It defaults to the `OpenCADC GitHub Repository <https://www.github.com/opencadc/vostools.git/>`__.

To build with a different respository and branch, build with the following build arguments:

============  =======
Variable      Purpose
------------  -------
`GIT_URL`     The URL of the Git repository that contains the `vos` submodule.  This is expected to be a fork of the `OpenCADC GitHub Repository <https://www.github.com/opencadc/vostools.git/>`__.
`GIT_BRANCH`  The branch to checkout from for the given Git URL.
============  =======

`docker build -t myorg/vostools:2.7-alpine .`

`docker build --build-arg GIT_URL=https://github.com/myfork/vostools.git -t myorg/vostools:2.7-alpine -f docker/2.7/Dockerfile .`

`docker build --build-arg GIT_URL=https://github.com/myfork/vostools.git --build-arg GIT_BRANCH=v2 -t myorg/vostools:3.6 -f docker/3.6/Dockerfile .`
