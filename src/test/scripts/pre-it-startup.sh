#! /bin/sh

# We have to provide a little wrapper script for exec-maven-plugin to background our job
$* > /dev/null 2>&1 &
echo $! > sinai-it.pid
exit 0
