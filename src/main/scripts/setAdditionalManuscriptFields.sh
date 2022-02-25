#!/bin/bash

# Sets on manuscript records those fields that can't be derived from the EMEL KatIkon database.

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

jq 'map({ id, "ark_s": { "set": .ark }, "thumbnail_identifier_s": { "set" : .thumbnail_identifier } })' \
| curl -X POST -H 'Content-Type: application/json' "$1/update?commit=true" --data-binary @-
