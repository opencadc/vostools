#!/bin/bash

export VOSPACE_WEBSERVICE="localhost"
mounted_dir="/tmp/vospace"
test_dir="${mounted_dir}/int_test"

fusermount -u $mounted_dir

../../scripts/mountvofs --vospace vos:CADCRegtest1 -v
if [[ $? -ne 0 ]]
then
    echo "Could not mount vospace for CADCRegtest1"
    exit 1
fi

ls -l ${test_dir}
if [[ $? -ne 0 ]]
then
    echo "Could not list mounted vospace ${test_dir}"
    exit 1
fi

cp test1.out ${test_dir}/test1.out
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "Copied to a locked file in ${test_dir}"
    exit 1
fi

cp test1.out ${test_dir}/locked_dir
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}/locked_dir
    echo "Copied to a locked directory in ${test_dir}/locked_dir"
    exit 1
fi

cp test1.out ${test_dir}/locked_link_to_test1.out
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "Copied to a locked link, pointing to a locked file in ${test_dir}"
    exit 1
fi

cp test1.out ${test_dir}/unlocked_link_to_test1.out
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "Copied to an unlocked link to a locked file in ${test_dir}"
    exit 1
fi

rm ${test_dir}/test1.out
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "Removed a locked file in ${test_dir}."
    exit 1
fi

rm ${test_dir}/locked_link_to_test1.out
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "Removed a locked link to a locked file in ${test_dir}."
    exit 1
fi

mv ${test_dir}/test1.out ${test_dir}/test2.out
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "Moved a locked file to an unlocked file in ${test_dir}"
    exit 1
fi

mv ${test_dir}/test2.out ${test_dir}/test1.out
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "Moved an unlocked file to a locked file in ${test_dir}"
    exit 1
fi

mv ${test_dir}/test2.out ${test_dir}/locked_dir
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "Moved an unlocked file to a locked dir in ${test_dir}"
    exit 1
fi

mv ${test_dir}/locked_link_to_test2.out ${test_dir}/test4.out
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "Moved a locked link file to an unlocked file in ${test_dir}"
    exit 1
fi

mv ${test_dir}/test2.out ${test_dir}/locked_dir/test2.out
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "Moved an unlocked file to an unlocked file in a locked directory in ${test_dir}"
    exit 1
fi

rmdir ${test_dir}/locked_dir
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "deleted a locked directory in ${test_dir}"
    exit 1
fi

mkdir ${test_dir}/locked_dir/locked_dir2
if [[ $? -eq 0 ]]
then
    ls -l ${test_dir}
    echo "Created a directory in a locked directory in ${test_dir}"
    exit 1
fi


date
exit 0
