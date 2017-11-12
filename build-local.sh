#!/bin/bash

VERSION=0.13.0-PVGDPR-Sandbox-001

DIR="$( cd "$(dirname "$0")" ; pwd -P )"


DEST_DIR=$DIR/../pontus-dist/opt/pontus/pontus-knox
ZIP_BUILD=$DIR/target/${VERSION}/knox-${VERSION}.zip
CURDIR=`pwd`

if [[ ! -d $DEST_DIR ]] ; then
  mkdir -p $DEST_DIR
fi

if [[ ! -f $ZIP_BUILD ]]; then
  mvn -Drat.numUnapprovedLicenses=100 -DskipTests -Ppackage install
fi

cd $DEST_DIR
rm -rf *
unzip $ZIP_BUILD 
ln -s knox-${VERSION}/ current

cd current
./bin/knoxcli.sh create-master --generate

tar xvfz $DIR/conf.tar.gz

cd $CURDIR
