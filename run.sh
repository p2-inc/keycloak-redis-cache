#!/bin/bash

mvn clean install -DskipTests && docker compose -f multi-docker-compose.yml up
