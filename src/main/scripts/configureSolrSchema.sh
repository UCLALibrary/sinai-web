#!/bin/bash

# Uses the Solr schema API to configure the core for the Sinai Scholars Site.
# This script assumes Solr version 8.11. See https://solr.apache.org/guide/8_11/schema-api.html for the schema API docs.

usage() {
    echo "Usage: $0 SOLR_CORE_URL"
}

if [ -z $1 ]
then
    usage
    exit 1
fi

curl -X POST -H 'Content-type: application/json' "$1/schema" --data-binary '{
    "add-field": [
        { "name": "keyword_t", "type": "text_general", "indexed": true, "stored": true },
        { "name": "record_type_s", "type": "string", "indexed": true, "stored": true, "required": true } ],
    "add-copy-field": [
        { "source": "*_s", "dest": [ "keyword_t" ] },
        { "source": "*_i", "dest": [ "keyword_t" ] } ]
}'
