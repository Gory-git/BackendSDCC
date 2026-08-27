#!/bin/bash
# User data dell'istanza EC2 (Amazon Linux 2023): eseguito una sola volta al
# primo avvio. Installa Docker, scarica la configurazione da S3 e accende lo
# stack. Non contiene segreti: le credenziali AWS arrivano dal ruolo IAM.
set -euxo pipefail

BUCKET="backendsdcc-dev-files-001-094028239135-eu-north-1-an"
APP_DIR=/opt/receipthub

dnf update -y
dnf install -y docker
systemctl enable --now docker

# Il plugin compose non è nei repository di Amazon Linux: si installa a mano.
mkdir -p /usr/local/lib/docker/cli-plugins
curl -sSL "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
chmod +x /usr/local/lib/docker/cli-plugins/docker-compose

usermod -aG docker ec2-user

mkdir -p "$APP_DIR"
cd "$APP_DIR"

aws s3 cp "s3://$BUCKET/deploy/compose.prod.yaml" ./compose.prod.yaml
aws s3 cp "s3://$BUCKET/deploy/Caddyfile" ./Caddyfile
aws s3 cp "s3://$BUCKET/deploy/update.sh" ./update.sh
chmod +x ./update.sh

# La password del database si genera qui e resta su questa macchina: non passa
# da S3 né da GitHub. Il volume di Postgres la conserva fra i riavvii, quindi
# va generata una volta sola, al primo boot.
if [ ! -f .env ]; then
    cat > .env <<ENVEOF
POSTGRES_PASSWORD=$(openssl rand -base64 24 | tr -d '/+=' | head -c 24)
SITE_ADDRESS=:80
PUBLIC_ORIGIN=http://localhost
BACKEND_IMAGE=nginx:alpine
FRONTEND_IMAGE=nginx:alpine
HTTP_PORT=80
HTTPS_PORT=443
ENVEOF
fi

# I segnaposto nginx servono solo a far partire lo stack prima del primo deploy:
# il primo push su master li sostituisce con le immagini vere.
docker compose -f compose.prod.yaml up -d db caddy || true

echo "istanza pronta: attendo il primo deploy da GitHub Actions"
