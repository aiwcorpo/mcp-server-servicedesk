#!/usr/bin/env bash
# First installation on a VM, as root: install.sh <mcp-servicedesk.jar>. Review before running.
set -euo pipefail

APP_USER=mcp
APP_DIR=/opt/mcp-servicedesk
CFG_DIR=/etc/mcp-servicedesk
DEPLOY_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR_SRC="${1:?usage: install.sh <path-to-mcp-servicedesk.jar>}"

id -u "$APP_USER" &>/dev/null || useradd --system --no-create-home --shell /usr/sbin/nologin "$APP_USER"

install -d -o root -g "$APP_USER" -m 0750 "$APP_DIR" "$CFG_DIR" "$CFG_DIR/tls"
install -o root -g "$APP_USER" -m 0640 "$JAR_SRC" "$APP_DIR/mcp-servicedesk.jar"
# The environment file holds passwords: created once from the template, never overwritten.
if [[ ! -e "$CFG_DIR/mcp-servicedesk.env" ]]; then
    install -o root -g "$APP_USER" -m 0640 "$DEPLOY_DIR/systemd/mcp-servicedesk.env.template" "$CFG_DIR/mcp-servicedesk.env"
fi
install -o root -g root -m 0644 "$DEPLOY_DIR/systemd/mcp-servicedesk.service" /etc/systemd/system/mcp-servicedesk.service
systemctl daemon-reload
echo "Installed. Next: fill $CFG_DIR/mcp-servicedesk.env, put keystore.p12 and truststore.p12 into $CFG_DIR/tls,"
echo "then: systemctl enable --now mcp-servicedesk"
