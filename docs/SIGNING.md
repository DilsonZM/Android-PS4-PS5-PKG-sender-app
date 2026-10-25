# Firma estable de releases

## Por qué

GitHub Actions genera una keystore debug nueva en cada ejecución. Eso hace que
cada APK tenga una firma distinta y Android rechace actualizar encima
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). A partir de **v1.0.3** el release se
firma con una keystore estable guardada en GitHub Secrets.

## Secretos necesarios

En **GitHub → Settings → Secrets and variables → Actions → New repository
secret**:

| Secreto | Valor |
|---|---|
| `ANDROID_KEYSTORE_BASE64` | contenido de `remotesend-release.jks.b64` |
| `ANDROID_KEYSTORE_PASSWORD` | contraseña del keystore |
| `ANDROID_KEY_ALIAS` | `remotesend` |
| `ANDROID_KEY_PASSWORD` | contraseña de la clave |

Los valores están en:

```text
~/.config/remotesend-signing/remotesend-release.jks
~/.config/remotesend-signing/remotesend-release.jks.b64
~/.config/remotesend-signing/credentials.env
```

También puedes imprimirlos con:

```bash
scripts/print-signing-secrets.sh
```

No subas la keystore ni las contraseñas al repositorio. Guarda una copia de
seguridad: si se pierde, no podrás actualizar las apps ya instaladas con la
misma firma.

## Cómo funciona el workflow

- Los pushes a `main` compilan debug, tests y lint (sin secretos).
- Los tags `v*` ejecutan el job `release`:
  1. Decodifica el keystore desde `ANDROID_KEYSTORE_BASE64`.
  2. Compila `assembleRelease` con `ANDROID_KEYSTORE_*`.
  3. Verifica la firma con `apksigner`.
  4. Publica `RemoteSend.apk` en el Release.

## Migración

v1.0.2 y anteriores están firmadas con claves debug. Para pasar a la firma
estable:

1. Desinstala RemoteSend una vez (los PKG de `Download` no se borran).
2. Instala `RemoteSend.apk` de v1.0.3 o superior.
3. A partir de ahí las actualizaciones se instalan encima.

## Generar otra keystore (opcional)

```bash
keytool -genkeypair -v \
  -keystore remotesend-release.jks \
  -alias remotesend \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storetype PKCS12 \
  -dname "CN=Dilson Zuleta Molina, OU=RemoteSend, O=DilsonZM, C=CO"
base64 -w0 remotesend-release.jks > remotesend-release.jks.b64
```
