#!/usr/bin/env bash
set -euo pipefail

mkdir -p ./logs
sudo chown -R "$USER:$USER" ./logs || true
sudo chmod -R 777 ./logs

