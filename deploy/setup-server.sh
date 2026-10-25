#!/usr/bin/env bash
# One-shot setup for a fresh Ubuntu VM (tested target: Oracle Cloud Always
# Free, Ampere A1, Ubuntu 24.04). Safe to re-run: every step checks first.
#
#   DOMAIN=documorph.duckdns.org \
#   MODAL_TOKEN_ID=ak-... MODAL_TOKEN_SECRET=as-... \
#   BRANCH=main bash setup-server.sh
#
# Generates the database password, JWT secret and pipeline key on the server
# itself, so they never pass through anyone's chat, email or clipboard.
set -euo pipefail

: "${DOMAIN:?set DOMAIN, e.g. documorph.duckdns.org}"
BRANCH="${BRANCH:-main}"
REPOS_DIR="$HOME"

step() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }

step "Docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sudo sh
fi
sudo usermod -aG docker "$USER" || true
DOCKER="sudo docker"

step "Firewall: allow HTTP and HTTPS"
# Oracle's Ubuntu images ship iptables rules that reject everything except SSH,
# even after the cloud security list allows a port. Open 80 and 443 here too.
for port in 80 443; do
  sudo iptables -C INPUT -p tcp --dport "$port" -j ACCEPT 2>/dev/null ||
    sudo iptables -I INPUT 1 -p tcp --dport "$port" -j ACCEPT
done
if command -v netfilter-persistent >/dev/null 2>&1; then
  sudo netfilter-persistent save
fi

step "Repositories (branch: $BRANCH)"
cd "$REPOS_DIR"
for repo in documorph documorph-nlp-pipeline; do
  if [ ! -d "$repo" ]; then
    git clone --branch "$BRANCH" "https://github.com/AtharvaS2005/$repo.git"
  else
    git -C "$repo" fetch origin "$BRANCH" && git -C "$repo" checkout "$BRANCH" &&
      git -C "$repo" pull --ff-only origin "$BRANCH"
  fi
done

step "Secrets (.env.prod)"
cd "$REPOS_DIR/documorph"
if [ ! -f .env.prod ]; then
  : "${MODAL_TOKEN_ID:?set MODAL_TOKEN_ID for the first run}"
  : "${MODAL_TOKEN_SECRET:?set MODAL_TOKEN_SECRET for the first run}"
  umask 077
  cat > .env.prod <<EOF
DOMAIN=$DOMAIN
DOCUMORPH_DB_PASSWORD=$(openssl rand -hex 32)
DOCUMORPH_JWT_SECRET=$(openssl rand -base64 48 | tr -d '\n')
PIPELINE_API_KEY=$(openssl rand -hex 32)
MODAL_TOKEN_ID=$MODAL_TOKEN_ID
MODAL_TOKEN_SECRET=$MODAL_TOKEN_SECRET
EOF
  echo "created .env.prod (readable by this user only)"
else
  echo ".env.prod already exists; leaving it unchanged"
fi

step "Build and start"
$DOCKER compose -f docker-compose.prod.yml --env-file .env.prod up -d --build
$DOCKER compose -f docker-compose.prod.yml --env-file .env.prod ps

step "Done"
echo "Site: https://$DOMAIN  (the certificate can take a minute on first start)"
