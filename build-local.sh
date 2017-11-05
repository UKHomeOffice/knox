#!/bin/bash

VERSION=0.13.0-SNAPSHOT

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"


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

cd $CURDIR
