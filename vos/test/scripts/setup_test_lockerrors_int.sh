#!/bin/bash

export VOSPACE_WEBSERVICE='localhost'
echo "This is a test file" > test1.out
../../scripts/vlock --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test islocked false
../../scripts/vrmdir --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test
../../scripts/vmkdir --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test
../../scripts/vcp test1.out --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/test1.out
echo "This is more to the test file." >> test1.out
../../scripts/vcp test1.out --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/test2.out
echo "This is even more to the test file.  In other words, make sure the md5 sums don't match." >> test1.out
../../scripts/vcp test1.out --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/test3.out
../../scripts/vmkdir --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/locked_dir
../../scripts/vcp test1.out --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/locked_dir/test1.out
../../scripts/vcp test1.out --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/locked_dir/test2.out
../../scripts/vln --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/test1.out vos:CADCRegtest1/int_test/locked_link_to_test1.out
../../scripts/vln --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/test1.out vos:CADCRegtest1/int_test/unlocked_link_to_test1.out
../../scripts/vln --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/test2.out vos:CADCRegtest1/int_test/locked_link_to_test2.out
../../scripts/vlock --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/test1.out islocked true
../../scripts/vlock --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/locked_dir islocked true
../../scripts/vlock --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/locked_link_to_test1.out islocked true
../../scripts/vlock --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test/locked_link_to_test2.out islocked true
../../scripts/vlock --cert=$A/test-certificates/x509_CADCRegtest1.pem vos:CADCRegtest1/int_test islocked true
