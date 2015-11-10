#! /bin/bash

# We're going to be opinionated about logging frameworks
LOG_DELEGATE="-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory"
KEY_PASS_CONFIG="-Dsinai.key.pass=${sinai.key.pass}"
SINAI_TEMP_DIR="-Dsinai.temp.dir=${sinai.temp.dir}"
SINAI_PORT="-Dsinai.port=${sinai.port}"
DROPWIZARD_METRICS="-Dvertx.metrics.options.enabled=true -Dvertx.metrics.options.registryName=sinai.metrics"
JMX_METRICS="-Dcom.sun.management.jmxremote -Dvertx.options.jmxEnabled=true"
AUTHBIND=""
SINAI_CONFIG=""

# If we have authbind and it's configured to run our port, let's use it
if hash authbind 2>/dev/null; then
  if [ -e "/etc/authbind/byport/${sinai.port}" ] ; then
    AUTHBIND="authbind"
  fi
fi

if [ -e "${sinai.json.config.path}" ]; then
  SINAI_CONFIG="-conf ${sinai.json.config.path}"
fi

$AUTHBIND java $LOG_DELEGATE $KEY_PASS_CONFIG $SINAI_TEMP_DIR $SINAI_PORT $DROPWIZARD_METRICS $1 \
  -jar target/sinai-web-${project.version}-exec.jar $SINAI_CONFIG
