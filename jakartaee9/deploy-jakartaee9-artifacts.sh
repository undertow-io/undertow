#!/bin/sh
# file: deploy-jakartaee9-artifacts.sh
#
# JBoss, Home of Professional Open Source.
# Copyright 2021 Red Hat, Inc., and individual contributors
# as indicated by the @author tags.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing, software
#  distributed under the License is distributed on an "AS IS" BASIS,
#  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#  See the License for the specific language governing permissions and
#  limitations under the License.
#

deploy_artifact(){
    version=$1
    module=$2

    jakarta_dir=$(pwd)
    jar_file=$jakarta_dir/target/output/undertow-$module-jakartaee9-$version.jar
    pom_file=$jakarta_dir/target/output/undertow-$module-jakartaee9-$version.pom
    if [ -e $jakarta_dir/../$module/target/undertow-$module-sources.jar ]
    then
        sources_file=$jakarta_dir/../$module/target/undertow-$module-sources.jar
    else
        sources_file=$jakarta_dir/../$module/target/undertow-$module-$version-sources.jar
    fi
    
    check_file_exists $jar_file
    check_file_exists $pom_file
    check_file_exists $sources_file

    mvn deploy:deploy-file -DrepositoryId=jboss-releases-repository -Durl=https://repository.jboss.org/nexus/service/local/staging/deploy/maven2 -DaltDeploymentRepository=jboss-releases-repository::default::https://repository.jboss.org/nexus/service/local/staging/deploy/maven2 -Pjboss-release -Drelease -Dfile=$jar_file -DpomFile=$pom_file -Dsources=$sources_file
}

check_file_exists(){
    if ! [ -e $1 ]
    then
       echo "ERROR: File $1 not found"
       exit 1
    fi  
}

version=$(mvn help:evaluate -Dexpression=project.version -q -DforceStdout)
deploy_artifact $version "servlet"
deploy_artifact $version "websockets-jsr"
deploy_artifact $version "examples"


