#!/usr/bin/env bash
set -euo pipefail
umask 077
[[ "$(hostname)" == VM-0-6-ubuntu ]] || { echo 'Wrong host' >&2; exit 1; }
readonly root=/srv/allo-nouilles
readonly domain=pos.allonouilles.com
readonly source_dir="/etc/letsencrypt/live/$domain"
readonly tls_dir="$root/private/tls"
if [[ -n "${RENEWED_DOMAINS:-}" && "${RENEWED_DOMAINS}" != "$domain" ]]; then exit 0; fi

openssl x509 -in "$source_dir/fullchain.pem" -noout -checkend 0 >/dev/null
openssl x509 -in "$source_dir/fullchain.pem" -noout -checkhost "$domain" >/dev/null
certificate_key="$(openssl x509 -in "$source_dir/fullchain.pem" -pubkey -noout | openssl pkey -pubin -outform DER | sha256sum)"
private_key="$(openssl pkey -in "$source_dir/privkey.pem" -pubout -outform DER | sha256sum)"
[[ "$certificate_key" == "$private_key" ]]
install -d -o root -g 101 -m 0750 "$tls_dir"
install -o root -g 101 -m 0640 "$source_dir/fullchain.pem" "$tls_dir/.fullchain.pending"
install -o root -g 101 -m 0640 "$source_dir/privkey.pem" "$tls_dir/.privkey.pending"
mv -f "$tls_dir/.fullchain.pending" "$tls_dir/fullchain.pem"
mv -f "$tls_dir/.privkey.pending" "$tls_dir/privkey.pem"
compose=(docker compose --project-name allo-nouilles --env-file "$root/private/runtime.env" -f "$root/compose.yml")
frontend_id="$("${compose[@]}" ps -q frontend)"
if [[ -n "$frontend_id" ]]; then
  [[ "$(docker inspect --format '{{index .Config.Labels "com.docker.compose.project"}}' "$frontend_id")" == allo-nouilles ]]
  docker exec "$frontend_id" nginx -t
  docker exec "$frontend_id" nginx -s reload
fi
printf '%s\n' "Certificate installed for $domain"
