#!/bin/bash

VERSION=0.12.0

DIR="$( cd "$(dirname "$0")" ; pwd -P )"


DEST_DIR=$DIR/../pontus-dist/opt/pontus/pontus-knox
ZIP_BUILD=$DIR/target/${VERSION}/knox-${VERSION}.zip
CURDIR=`pwd`

if [[ ! -d $DEST_DIR ]] ; then
  mkdir -p $DEST_DIR
fi

cd $DIR
if [[ ! -f $ZIP_BUILD ]]; then
  mvn -Drat.numUnapprovedLicenses=100 -DskipTests -Ppackage install
fi
tar cvfz $DIR/conf.tar.gz data/services/pvgdpr-gui/0.0.1/rewrite.xml data/services/pvgdpr-gui/0.0.1/service.xml data/services/pvgdpr-graph/0.0.1/rewrite.xml data/services/pvgdpr-graph/0.0.1/service.xml data/services/pvgdpr-server/0.0.1/rewrite.xml data/services/pvgdpr-server/0.0.1/service.xml data/services/nifi/0.0.1/rewrite.xml data/services/nifi/0.0.1/service.xml data/services/nifiapi/0.0.1/rewrite.xml data/services/nifiapi/0.0.1/service.xml data/services/nifi-content-viewer/0.0.1/rewrite.xml data/services/nifi-content-viewer/0.0.1/service.xml data/services/nifi-docs/0.0.1/rewrite.xml data/services/nifi-docs/0.0.1/service.xml conf/topologies/sandbox.xml

cd $DEST_DIR
rm -rf *
unzip $ZIP_BUILD 
ln -s knox-${VERSION}/ current

cd current
rm -rf ./data/security/*
./bin/knoxcli.sh create-master --generate

tar xvfz $DIR/conf.tar.gz

cd $CURDIR
