#! /bin/bash

#
# Script to convert the Let's "Encrypt" output to a Java Keystore
#
#   Usage: ./letsencrypt2jks.sh [DOMAIN] [JKS_PASSWORD]
#

hash openssl 2>/dev/null || { echo >&2 "I require openssl but it's not installed.  Aborting."; exit 1; }

if [ "$#" -ne 2 ]; then
  "Usage: target/letsencrypt2jks.sh [DOMAIN] [PASSWORD]"
fi

sudo rm -f le_sinai.jks

sudo openssl pkcs12 -export \
  -in "/etc/letsencrypt/live/$1/fullchain.pem" \
  -inkey "/etc/letsencrypt/live/$1/privkey.pem" \
  -out "/tmp/sinai_cert_and_key.p12" \
  -name "sinai" \
  -caname "root" \
  -password "pass:$2"

sudo keytool -importkeystore \
  -deststorepass "$2" \
  -destkeypass "$2" \
  -destkeystore "le_sinai.jks" \
  -srckeystore "/tmp/sinai_cert_and_key.p12" \
  -srcstoretype "PKCS12" \
  -srcstorepass "$2" \
  -alias "sinai"
