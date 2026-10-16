#!/bin/bash
set -euo pipefail
trap 'echo "HTTPS setup failed at line ${LINENO}; inspect /var/log/eb-engine.log and /var/log/letsencrypt/letsencrypt.log." >&2' ERR

domain=api-dev.swampdragoninn.casa
email=$(/opt/elasticbeanstalk/bin/get-config environment -k LETSENCRYPT_EMAIL)
if [[ -z "$email" || "$email" != *@* || "$email" == *$'\n'* ]]; then
    echo "Set LETSENCRYPT_EMAIL to a valid certificate account contact before deployment." >&2
    exit 1
fi

dnf install -y python3.11 python3.11-pip
if [[ ! -x /opt/trackmyboardgames-certbot/bin/python ]]; then
    python3.11 -m venv /opt/trackmyboardgames-certbot
fi
/opt/trackmyboardgames-certbot/bin/python -m pip install \
    --requirement .platform/scripts/certbot-requirements.txt

# Boto3 obtains short-lived credentials from the EC2 instance role.
/opt/trackmyboardgames-certbot/bin/certbot certonly \
    --dns-route53 \
    --non-interactive \
    --agree-tos \
    --email "$email" \
    --cert-name "$domain" \
    --domain "$domain" \
    --keep-until-expiring

test -s "/etc/letsencrypt/live/$domain/fullchain.pem"
test -s "/etc/letsencrypt/live/$domain/privkey.pem"

install -d -m 755 /etc/letsencrypt/renewal-hooks/deploy
cat > /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh <<'HOOK'
#!/bin/bash
set -euo pipefail
if systemctl is-active --quiet nginx; then
    /usr/sbin/nginx -t
    systemctl reload nginx
fi
HOOK
chmod 755 /etc/letsencrypt/renewal-hooks/deploy/reload-nginx.sh

cat > /etc/systemd/system/trackmyboardgames-certbot.service <<'SERVICE'
[Unit]
Description=Renew TrackMyBoardGames HTTPS certificate
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
ExecStart=/opt/trackmyboardgames-certbot/bin/certbot renew --non-interactive
SERVICE

cat > /etc/systemd/system/trackmyboardgames-certbot.timer <<'TIMER'
[Unit]
Description=Check TrackMyBoardGames certificate renewal twice daily

[Timer]
OnCalendar=*-*-* 00,12:00:00
RandomizedDelaySec=3600
Persistent=true

[Install]
WantedBy=timers.target
TIMER

systemctl daemon-reload
systemctl enable --now trackmyboardgames-certbot.timer
