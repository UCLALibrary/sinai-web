#!/bin/bash

# Makes a list of manuscripts visible to end users of the Sinai Scholars site.

usage() {
    cat <<EOF
Usage: $0 SOLR_CORE_URL <<< '[
    {
        "id": "00000000-0000-0000-0000-000000000000",
        "ark": "ark:/00000/00000000",
        "thumbnail_identifier": "ark:%2F00000%2F11111111/0,1000,4000,4000/200,200/0/default.jpg"
    }, ...
]'
EOF
}

if [ -z $1 ]
then
    usage
    exit 1
fi

jq 'map({ id, "publish_b": { "set": true } })' \
| curl -X POST -H 'Content-Type: application/json' "$1/update?commit=true" --data-binary @-
