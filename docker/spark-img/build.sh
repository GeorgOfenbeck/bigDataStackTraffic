#!/bin/bash

set -e

TAG=3.5.4-hadoop3.4.1

build() {
  NAME=$1
  IMAGE=ofenbeck/spark-$NAME:$TAG
  cd $([ -z "$2" ] && echo "./$NAME" || echo "$2")
  echo '--------------------------' building $IMAGE in $(pwd)
  docker build -t $IMAGE .
  cd -
}

if [ $# -eq 0 ]; then
  build base
  build master
  build worker
else
  build $1 $2
fi
