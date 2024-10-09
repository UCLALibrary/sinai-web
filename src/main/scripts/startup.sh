#! /bin/bash

SOLR_SERVER="-Dsinai.solr.server='https://p-u-sinaipalimpsolr01.library.ucla.edu/solr/sinaimetaprod'"
IMAGE_SERVER="-Dsinai.solr.server='https://sinai-images.library.ucla.edu'"

# We're going to be opinionated about logging frameworks
LOG_DELEGATE="-Dvertx.logger-delegate-factory-class-name=io.vertx.core.logging.SLF4JLogDelegateFactory"
LOG_CONFIG_DIR="-Xbootclasspath/p:${sinai.logging.config.dir}"
LOG_OUTPUT_DIR="-Dsinai.logs.dir=${sinai.logs.dir}"
KEY_PASS_CONFIG="-Dsinai.key.pass=${sinai.key.pass}"
SINAI_TEMP_DIR="-Dsinai.temp.dir=${sinai.temp.dir}"
SINAI_PORT="-Dsinai.port=${sinai.port} -Dsinai.redirect.port=${sinai.redirect.port}"
DROPWIZARD_METRICS="-Dvertx.metrics.options.enabled=true -Dvertx.metrics.options.registryName=sinai.metrics"
JMX_METRICS="-Dcom.sun.management.jmxremote -Dvertx.metrics.options.jmxEnabled=true"
# For tools like Eclipse's Debugging
JDWP_AGENTLIB="-agentlib:jdwp=transport=dt_socket,address=9003,server=y,suspend=n"
# For tools like visualvm or jconsole (Note: only for use on dev's localhost since there is no configured security)
JMXREMOTE="-Dcom.sun.management.jmxremote.port=9001 -Dcom.sun.management.jmxremote.authenticate=false"
JMXREMOTE="$JMXREMOTE -Dcom.sun.management.jmxremote.ssl=false"
AUTHBIND=""
SINAI_CONFIG=""
SINAI_HOST="-Dsinai.host=${SINAI_HOST:-localhost}"
SINAI_AUTH_KEY="-Dsinai.id.key=${sinai.id.key}"
JDBC_DRIVER="-Djdbc.drivers=org.postgresql.Driver"
SINAI_METADATA_HARVEST_INTERVAL="-Dsinai.metadata.harvest.interval="
SINAI_SEARCH_TIMEOUT="-Dsinai.search.timeout="

# If we have authbind and it's configured to run our port, let's use it
if hash authbind 2>/dev/null; then
  if [ -e "/etc/authbind/byport/${sinai.port}" ] ; then
    AUTHBIND="authbind"
  fi
fi

if [ -e "${sinai.json.config.path}" ]; then
  SINAI_CONFIG="-conf ${sinai.json.config.path}"
fi

if [[ "${dev.tools}" == *"JDWP_AGENTLIB"* ]]; then
  echo "Using JDWP_AGENTLIB for JMX connections (port 9003)"
  JMX_METRICS="$JMX_METRICS $JDWP_AGENTLIB"
fi

if [[ "${dev.tools}" == *"JMX_REMOTE"* ]]; then
  echo "Using JMX_REMOTE for JMX connections (port 9001)"
  JMX_METRICS="$JMX_METRICS $JMX_REMOTE"
fi

$AUTHBIND java $IMAGE_SERVER $LOG_DELEGATE $KEY_PASS_CONFIG $SINAI_TEMP_DIR $SINAI_PORT \
  $LOG_CONFIG_DIR $LOG_OUTPUT_DIR \
  $DROPWIZARD_METRICS $SOLR_SERVER $JMX_METRICS $SINAI_HOST $SINAI_AUTH_KEY $JDBC_DRIVER \
  $SINAI_METADATA_HARVEST_INTERVAL $SINAI_SEARCH_TIMEOUT \
  $1 -jar ${project.build.directory}/build-artifact/${project.artifactId}-${project.version}.jar $SINAI_CONFIG
