#!/bin/sh
# Eseguito sull'istanza da GitHub Actions tramite SSM: aggiorna un solo servizio
# all'immagine appena pubblicata su ECR.
#   uso: update.sh <backend|frontend> <immagine-ecr-completa>
set -eu

SERVICE="${1:?servizio mancante}"
IMAGE="${2:?immagine mancante}"
APP_DIR=/opt/receipthub
AWS_REGION="${AWS_REGION:-eu-north-1}"

case "$SERVICE" in
    backend)  KEY=BACKEND_IMAGE ;;
    frontend) KEY=FRONTEND_IMAGE ;;
    *) echo "servizio sconosciuto: $SERVICE" >&2; exit 1 ;;
esac

cd "$APP_DIR"

# Le credenziali arrivano dal ruolo IAM dell'istanza: nessun segreto sul disco.
REGISTRY="${IMAGE%%/*}"
aws ecr get-login-password --region "$AWS_REGION" \
    | docker login --username AWS --password-stdin "$REGISTRY"

# Il tag dell'immagine vive nel .env, così compose.prod.yaml resta identico
# a quello che si usa in locale.
if grep -q "^${KEY}=" .env; then
    sed -i "s|^${KEY}=.*|${KEY}=${IMAGE}|" .env
else
    echo "${KEY}=${IMAGE}" >> .env
fi

docker compose -f compose.prod.yaml pull "$SERVICE"
docker compose -f compose.prod.yaml up -d "$SERVICE"

# Senza questo il disco si riempie di immagini vecchie a ogni deploy.
docker image prune -f

echo "aggiornato $SERVICE -> $IMAGE"
docker compose -f compose.prod.yaml ps
