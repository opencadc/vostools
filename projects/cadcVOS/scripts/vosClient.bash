#!/bin/bash

java -jar ${CADC_ROOT}/lib/cadcVOSClient.jar $*
progStatus=$?
exit $progStatus

