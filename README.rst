VOS - VOSpace tools


.. image:: https://img.shields.io/pypi/pyversions/vos.svg
    :target: https://pypi.python.org/pypi/vos

.. image:: https://github.com/opencadc/vostools/workflows/CI/badge.svg?branch=master&event=schedule
    :target: https://github.com/opencadc/vostools/actions?query=event%3Aschedule+

.. image:: https://codecov.io/gh/opencadc/vostools/branch/master/graph/badge.svg
  :target: https://codecov.io/gh/opencadc/vostools

.. image:: https://img.shields.io/github/contributors/opencadc/vostools.svg
    :target: https://github.com/opencadc/vostools/graphs/contributors


Tools to work with VOSpace services (primarily the CADC ones)..


Developers Guide
================


Requires pip.

Installing Packages
-------------------
Note: might need to escape chars in your shell

::

    cd vos && pip install -e .[test]

Testing packages
----------------

Testing vos
~~~~~~~~~~~

::

    cd ./vos
    pytest vos



Checkstyle
~~~~~~~~~~
flake8 style checking is enforced on pull requests. Following commands should
not report errors

::

     flake8 vos/vos


Testing with tox
~~~~~~~~~~~~~~~~

If tox, the generic virtual environment tool, is available it can be used to test with different versions of
python is isolation. For example, to test on all supported versions of Python in cadcdata (assuming that
they are available in the system):

::

    cd ./vos && tox

To test a specific version:

::

    cd ./vos && tox -e py3.9


To list all the available environments:

::

    cd ./vos && tox -a


