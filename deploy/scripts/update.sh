#!/usr/bin/env bash
# Replaces the jar and restarts the service, keeping the previous jar for rollback. Run as root.
set -euo pipefail
APP_DIR=/opt/mcp-servicedesk
JAR_SRC="${1:?usage: update.sh <path-to-new.jar>}"
cp -a "$APP_DIR/mcp-servicedesk.jar" "$APP_DIR/mcp-servicedesk.jar.bak.$(date +%Y%m%d%H%M%S)"
install -o root -g mcp -m 0640 "$JAR_SRC" "$APP_DIR/mcp-servicedesk.jar"
systemctl restart mcp-servicedesk
systemctl --no-pager status mcp-servicedesk | head -n 15
