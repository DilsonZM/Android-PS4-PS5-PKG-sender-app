# Pruebas y validación

## 1. Suite automatizada

Actualmente hay **55 pruebas** unitarias/de integración local (JVM), sin
dependencia de una consola.

```bash
./gradlew :app:testDebugUnitTest
```

Áreas cubiertas:

| Área | Pruebas |
|---|---|
| Parser PKG/PARAM.SFO | Cabecera, tabla, SFO, icono, archivos truncados |
| Servidor HTTP | Heartbeat, paquete completo, `Range`, `HEAD`, 416, 404 |
| RangeHeader | Rangos cerrados, abiertos, sufijo, límites |
| Cliente PS4 RPI | Instalación, ya instalado, progreso, hex, errores, timeout |
| Cliente TCP | PS5, GoldHEN, respuesta no-cero, conexión rechazada |
| Detector | AUTO→RPI, fallo explícito, host vacío, estado compartido |
| Escáner LAN | Detección de RPI en puerto personalizado |
| Cola | Reordenación, siguiente, eliminación de finalizados |
| Nombres/categorías | Title ID, Content ID, nombres seguros, bytes, duraciones |
| Red | IPv4 válida/privada, subred, 253 hosts |
| Backup | Serialización/deserialización y compatibilidad futura |

## 2. Validación en dispositivo real

Dispositivo: **NX769J / RedMagic, Android 16**.  
Consola: **PS4 con PS4RPI de njzydark (port OOSDK de flatZ RPI) en
192.168.18.12:12801**.  
Teléfono: `wlan0`, 192.168.18.119, Wi-Fi 5 GHz.

Resultados:

- **Servidor HTTP**: `/hb` responde `status=ok`, `/api/packages` y descarga con
  `Range` comprobados desde la laptop.
- **Escaneo de red**: 253/253 hosts; encontró `192.168.18.12` con puerto `12801`
  y sugirió `PS4 · flatZ RPI`. Al tocar el resultado, la IP/puerto se aplicaron.
- **Auto-escaneo**: concedido `MANAGE_EXTERNAL_STORAGE`; importó **10 PKG** desde
  `/storage/emulated/0/Download` al iniciar. La sincronización manual funciona.
- **Agrupación**: CUSA29952 mostró “Juego base” (v01.00) y “Actualización”
  (v01.06) bajo un mismo encabezado; CUSA13248 y CUSA54308 también agruparon.
- **Carátula**: la cola mostró el icono del juego y el CUSA.
- **Instalación real**: TABS CUSA29952 de 1,93 GB.
  - Progreso `0 → 100 %`.
  - Velocidad ~11 MB/s (10,8–11,4).
  - Bytes: hasta `2.044.592.128 / 2.068.054.016`.
  - ETA visible y decreciente.
  - La app permaneció viva; el proceso finalizó `COMPLETED`.
- **Cola real y auto-avance**: CUSA54308.
  - Juego base (576 MB) → COMPLETED (task 447).
  - Actualización (286 MB) → INSTALLING automáticamente (task 449) → COMPLETED.
  - DLC (1 MB) → INSTALLING automáticamente (task 450) → COMPLETED.
  - Velocidades 11,2–11,6 MB/s; sin intervención manual.
- **Eliminación**: el PKG de prueba CUSA09311 se borró de la biblioteca y del
  almacenamiento (`/sdcard/Download`); la biblioteca pasó de 10 a 9.
- **Contraer/expandir global**: se contrajeron los 4 CUSA, se reinició la app y
  el estado se mantuvo; el botón mostró “Expandir todo”.
- **Confirmaciones**: se mostró diálogo Material 3 al quitar de la cola y al
  eliminar un PKG; la acción de cola no borra el archivo.
- **Crash corregido**: el `SocketException: Broken pipe` de la descarga ya se
  captura; no cierra la app.
- **Hex corregido**: `0x7B440000` y `0x29CE0000` se parsean correctamente.
- **Servicio**: notificación y wake lock activos; la descarga continuó con la app
  en segundo plano.
- **FPS**: ajuste persistido en `FPS_120`; la solicitud de modo de pantalla se
  aplica en `MainActivity` (verificación visual en dispositivo 120 Hz).
- **UI final**: buscador tipo píldora, agrupación como icono, lote como icono
  dentro del banner CUSA, contraer/expandir global persistente.

## 3. Pruebas manuales pendientes

- PS5 etaHEN: instalación y respuesta `{res:0}`.
- PS4 GoldHEN: instalación con `CONTENT_ID`.
- Pausa/reanudación/stop en RPI.
- Auto-avance con varios PKG y lote completo CUSA.
- Backup/restore entre dos dispositivos.
- Verificación de 120 Hz con medidor de frecuencia.
- Recuperación tras matar el proceso durante una instalación.
- Red con router Gigabit y Ethernet USB-C en el teléfono.

## 4. Pruebas de rendimiento

Desde la laptop al servidor del teléfono:

```bash
curl -o /dev/null --max-time 10 \
  "http://192.168.18.119:8337/pkg/<id>/<archivo>.pkg"
```

Resultado observado: ~22 MB/s (dos saltos Wi-Fi, 5 GHz). La PS4 recibió
~11 MB/s, por lo que el cuello de botella está en PS4/LAN/RPI, no en la app.

## 5. Lint y build

```bash
./gradlew :app:lintDebug :app:assembleDebug
```

Lint pasa (solo advertencias de versiones y del permiso de almacenamiento). El
APK debug se genera en `app/build/outputs/apk/debug/RemoteSend.apk`.

## 6. Seguridad

- No hay telemetría ni llamadas a Internet salvo el servidor local.
- El servidor no tiene autenticación; es para LAN de confianza.
- `MANAGE_EXTERNAL_STORAGE` es opcional; sin él se usa SAF.
- Los logs pueden contener rutas e IPs locales; se pueden borrar o exportar.
