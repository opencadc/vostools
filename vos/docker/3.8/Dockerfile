FROM python:3.8-alpine

ARG GIT_URL='https://github.com/opencadc/vostools.git'
ARG GIT_BRANCH='master'

RUN apk --no-cache add curl gcc git libffi-dev libxslt-dev make musl-dev openssl-dev tcsh

RUN pip --disable-pip-version-check --no-cache-dir install pylint \
    #
    # Install pytest and mock
    && pip --disable-pip-version-check --no-cache-dir install pytest mock \
    # Install dependencies
    && pip --disable-pip-version-check --no-cache-dir install cadcutils flake8 autopep8

RUN mkdir -p /usr/src/app \
    && cd /usr/src/app \
    && git clone -b ${GIT_BRANCH} ${GIT_URL} vostools \
    && cd vostools/vos \
    && pip install .

RUN apk --no-cache del gcc make

WORKDIR /usr/src/app/vostools
