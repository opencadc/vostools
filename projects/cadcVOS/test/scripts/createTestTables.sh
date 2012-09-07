#!/bin/bash

##
## This script copies the source SQL DDLs from sql/<db arch> into build/tmp,
## applies modifications (if necessary) for creation in the cadctest database,
## drops all existing caom2 tables, and then creates new ones using credentials
## from $HOME/.dbrc
##
## required .dbrc setup:
##
## VOSPACE_WS_TEST cadctest <username> <password> net.sourceforge.jtds.jdbc.Driver jdbc:jtds:sybase://devsybase:4200/cadctest
##
## current modifications:
##
## sybase: none (uses cadctest and default schema == username)

doitSYB()
{
    RUNCMD=$1

    SQLDIR=sql
    TMPSQL=build/tmp/sybase
    \rm -rf $TMPSQL
    mkdir -p $TMPSQL
    \cp -f $SQLDIR/*.sql $TMPSQL

    $RUNCMD -i $TMPSQL/vospace.drop_all.sql
    $RUNCMD -i $TMPSQL/vospace.Node-SybaseASE.sql
}

## Sybase test setup
echo
if [ $(grep cadctest ~/.dbrc | grep -c '^VOSPACE_WS_TEST ') = 1 ]; then
    echo "found: VOSPACE_WS_TEST cadctest ... creating Sybase tables"
else
    echo "not found: VOSPACE_WS_TEST cadctest ..."
    exit 1
fi
echo

CRED=$(grep '^VOSPACE_WS_TEST ' ~/.dbrc | awk '{print $3,$4}')
DBUSER=$(echo $CRED | awk '{print $1}')
DBPW=$(echo $CRED | awk '{print $2}')

doitSYB "sqsh -S DEVSYBASE -D cadctest -U $DBUSER -P $DBPW"
