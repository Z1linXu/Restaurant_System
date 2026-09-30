#!/usr/bin/env bash
set -euo pipefail
# Run as root from a systemd timer. Existing certbot webroot manager only.
certbot renew --non-interactive \
  --config-dir /home/ubuntu/Restaurant_System/deployment/cloud/data/letsencrypt \
  --work-dir /var/lib/restaurant-staging-certbot \
  --logs-dir /var/log/restaurant-staging-certbot \
  --cert-name staging-pos.lanzhounoodlesmtl.com \
  --deploy-hook 'docker exec cloud-nginx-1 nginx -t && docker exec cloud-nginx-1 nginx -s reload'
