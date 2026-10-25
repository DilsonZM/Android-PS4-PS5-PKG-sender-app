#!/usr/bin/env bash
set -euo pipefail

DIR="${1:-$HOME/.config/remotesend-signing}"
CREDENTIALS="$DIR/credentials.env"
KEYSTORE_B64="$DIR/remotesend-release.jks.b64"

if [[ ! -f "$CREDENTIALS" || ! -f "$KEYSTORE_B64" ]]; then
  echo "No existen los archivos de firma en $DIR"
  exit 1
fi

# shellcheck disable=SC1090
source "$CREDENTIALS"

echo "Crea estos secretos en GitHub → Settings → Secrets and variables → Actions:"
echo
echo "ANDROID_KEYSTORE_BASE64:"
cat "$KEYSTORE_B64"
echo
echo "ANDROID_KEYSTORE_PASSWORD: $ANDROID_KEYSTORE_PASSWORD"
echo "ANDROID_KEY_ALIAS: $ANDROID_KEY_ALIAS"
echo "ANDROID_KEY_PASSWORD: $ANDROID_KEY_PASSWORD"
