#!/usr/bin/env bash

sbt "project app" docker:publishLocal

docker-compose up