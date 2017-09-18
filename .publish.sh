#!/usr/bin/env bash
# Deploys a Python library or application

product=$(echo $TRAVIS_TAG | awk -F '=' '{print $1}')
version=$(echo $TRAVIS_TAG | awk -F '=' '{print $2}')
if [ -z "$product" ]; then
    echo "Could not find name of product in TRAVIS_TAG: $TRAVIS_TAG";
    exit -1
fi
if [ -z "$version" ]; then
    echo "Could not find version of product in TRAVIS_TAG: $TRAVIS_TAG"; 
    exit -1
fi

echo "Deploying $product, version $version..."
cd $product || { echo "Project $product does not exist in this repo"; exit -1; }
# check that the version in the tag and in setup.cfg match
sed 's/ //g' setup.cfg | grep "^version=$version" || { \
   echo "Version in tag ($version) does not match version in setup.cfg \
($(sed 's/ //g' setup.cfg | grep '^version=' | awk -F '=' '{print $2}'))"; \
   exit -1; }

# build
python setup.py clean sdist || { echo "Errors building $product"; exit -1; }
# upload to pypi
# generate the .pypirc file first
echo "[pypi]" > .pypirc
chmod 600 .pypirc
echo "username = Canadian.Astronomy.Data.Centre" >> .pypirc
echo "password = ${PYPI_PASSWORD}" >> .pypirc

echo "Publish on pypi"
twine upload --config-file .pypirc dist/* || { echo "Errors publishing $TRAVIS_TAG"; exit -1; }

# check version available
pip uninstall -y $product
pip install --upgrade --pre $product==$version || { echo "$TRAVIS_TAG not installed on pypi" ; exit -1; } 
