#!/usr/bin/env bash
export JAVA_OPTS="-XX:TieredStopAtLevel=1"
./gradlew :runelite-client:run