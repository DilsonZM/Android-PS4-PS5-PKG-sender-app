#!/usr/bin/env bash
set -euo pipefail

TARGET="${1:?Uso: $0 <ip:puerto> [ruta-apk]}"
APK="${2:-app/build/outputs/apk/debug/RemoteSend.apk}"
ADB="${ADB:-adb}"

if [[ ! -f "$APK" ]]; then
  echo "No existe el APK: $APK"
  echo "Compila primero con: ./gradlew :app:assembleDebug"
  exit 1
fi

echo "Conectando a $TARGET..."
"$ADB" connect "$TARGET"
echo "Instalando $APK..."
"$ADB" -s "$TARGET" install -r "$APK"
echo "Abriendo RemoteSend..."
"$ADB" -s "$TARGET" shell am start -n com.remotesend.app/.MainActivity
