#!/bin/sh
# Allinea il record DuckDNS all'IP pubblico corrente dell'istanza.
# Girando dall'istanza stessa, il parametro ip= può restare vuoto: DuckDNS usa
# l'indirizzo da cui arriva la richiesta, che è esattamente quello che serve.
# Il token sta in /opt/receipthub/duckdns.env, leggibile solo da root: non passa
# né da S3 né da GitHub.
set -eu

CONF=/opt/receipthub/duckdns.env
[ -f "$CONF" ] || { echo "manca $CONF" >&2; exit 1; }
. "$CONF"

: "${DUCKDNS_DOMAIN:?DUCKDNS_DOMAIN non impostato}"
: "${DUCKDNS_TOKEN:?DUCKDNS_TOKEN non impostato}"

RISPOSTA=$(curl -fsS --max-time 20 \
    "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN}&token=${DUCKDNS_TOKEN}&ip=")

echo "$(date -Is) duckdns: $RISPOSTA" >> /var/log/duckdns.log

# DuckDNS risponde con la stringa "OK" oppure "KO": senza questo controllo un
# token sbagliato passerebbe inosservato, perché l'HTTP resta 200.
[ "$RISPOSTA" = "OK" ] || { echo "DuckDNS ha risposto $RISPOSTA" >&2; exit 1; }
