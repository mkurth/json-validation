#!/usr/bin/env bash

sbt test

docker pull redis:alpine
docker-compose up -d

sleep 5

curl http://localhost:8080/schema/config-schema -X POST -d @config-schema.json
curl http://localhost:8080/validate/config-schema -X POST -d @config.json

docker-compose down