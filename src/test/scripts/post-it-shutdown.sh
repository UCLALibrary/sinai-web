#! /bin/bash

# And we do a little clean up after the integration tests have been run
kill `cat sinai-it.pid`
rm sinai-it.pid
