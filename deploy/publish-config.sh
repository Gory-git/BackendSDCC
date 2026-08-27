#!/bin/sh
# Carica su S3 la configurazione che l'istanza legge all'avvio.
# Da eseguire dal proprio PC quando compose.prod.yaml o Caddyfile cambiano,
# poi rilanciare update.sh sull'istanza (o riavviarla).
set -eu

BUCKET="${AWS_S3_BUCKET:?esporta AWS_S3_BUCKET con il nome del bucket}"

aws s3 cp compose.prod.yaml "s3://$BUCKET/deploy/compose.prod.yaml"
aws s3 cp Caddyfile "s3://$BUCKET/deploy/Caddyfile"
aws s3 cp deploy/update.sh "s3://$BUCKET/deploy/update.sh"

echo "configurazione pubblicata su s3://$BUCKET/deploy/"
