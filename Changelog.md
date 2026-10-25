# Changelog

## 1.0.4 — 2026-10-25

- Nueva pantalla **Juegos instalados (Fase 1)**, accesible desde el botón flotante **Instalados** en Paquetes.
- Lista real de instalados leída de `app.db` por FTP de GoldHEN (puerto 2121).
- Portadas de `/user/appmeta`, CUSA, tamaño real y dona de distribución.
- Fallback secuencial a `is_exists` de PS4RPI si el FTP no está disponible.
- Desinstalación de juego/actualización/DLC con confirmación Material 3.
- `is_exists` secuencial: PS4RPI se cierra con CE-34878-0 si recibe varias consultas a la vez.
- Aviso claro: PS4RPI no reporta espacio libre total ni confirma updates/DLC.
- 58 pruebas unitarias/de integración.
- Version 1.0.4 (versionCode 5).

## 1.0.3 — 2026-10-25

- Firma estable de Releases con keystore en GitHub Actions.
- El APK firmado estable permite actualizar sin desinstalar a partir de esta
  versión.
- Instrucciones de secretos en `docs/SIGNING.md`.
- Version 1.0.3 (versionCode 4).

## 1.0.2 — 2026-10-25

- Icono de café en la barra superior de Inicio y botón de apoyo en
  Ajustes → Creador.
- Enlace de donación voluntaria por PayPal: https://paypal.me/dilsonzuleta
- No usa Google Play Billing; abre PayPal en el navegador/app.
- Versión 1.0.2 (versionCode 3).

## 1.0.1 — 2026-10-25

- Crédito explícito a **njzydark/PS4RPI**, port OOSDK de flatZ RPI que se
  instala en la PS4 y usa el puerto **12801** por defecto.
- La detección automática y el escaneo prueban 12800 (flatZ) y 12801 (PS4RPI).
- La instalación persiste el puerto detectado para pausa/reanudación/stop.
- El APK de build y Release se llama **RemoteSend.apk**.
- Banner CUSA con etiquetas compactas Juego/Act./DLC y `FlowRow`.
- Sección **Creador** en Ajustes y créditos del autor Dilson Zuleta Molina
  (DilsonZM).
- Créditos a DeepSeek v4.1 flash.

## 1.0.0 — 2026-10-24

### Añadido

- App Android Kotlin + Jetpack Compose Material 3.
- Servidor HTTP embebido con `/hb`, `/api/packages`, `/pkg/{id}` e `/icon/{id}`.
- Soporte de `Range`, `HEAD` y streaming sin cargar el PKG en memoria.
- Cliente PS4 flatZ RPI (instalar, pausar, reanudar, detener, desregistrar).
- Cliente PS5 etaHEN y PS4 GoldHEN por TCP/JSON.
- Detección automática y escaneo LAN de 253 hosts con puertos configurables.
- Importación SAF de archivos y carpetas.
- Auto-escaneo de `Download` con acceso opcional a todos los archivos.
- Carpeta vinculada persistente y sincronización manual.
- Parser PKG/PARAM.SFO e icono `icon0`.
- Agrupación por CUSA con juego base, actualizaciones y DLC.
- “Añadir todo el lote a la cola”.
- Carátula del juego en la cola y en la tarjeta de instalación activa.
- Progreso con %, bytes, velocidad y ETA.
- Parser de contadores hexadecimales de RPI (`0x...`).
- Cola persistente con auto-avance y estados.
- Servicio en primer plano, wake lock y notificaciones.
- Tema claro/oscuro, color dinámico y selector 60/120 FPS.
- Backup/restore de cola en JSON.
- Registros, modo desarrollador y documentación.
- 54 pruebas unitarias/de integración.

### Corregido

- Crash de la app por `SocketException: Broken pipe` cuando la consola corta
  una descarga.
- Progreso atascado en 0 % por contadores hex de PS4.
- Paquetes nuevos que no aparecían por no re-escanear Download.
- El escáner no encontraba consolas en puertos personalizados como 12801.

### Validado

- PS4 RPI en 192.168.18.12:12801.
- 10 PKG auto-importados desde Download.
- Instalación real de CUSA29952 de 1,93 GB con progreso y velocidad.
- Cola real CUSA54308: juego base → actualización → DLC, completados por
  auto-avance (tasks 447, 449 y 450).
- Escaneo LAN encontrando la PS4 en el puerto personalizado 12801.
- Borrado real de un PKG de `Download` desde la biblioteca.
- Contraer/expandir global persistido entre reinicios.

### UI final

- Buscador tipo píldora.
- Agrupación por CUSA como icono en la barra superior.
- Icono redundante de cola eliminado de la barra de Paquetes.
- Contraer/expandir global como icono en la fila de carpeta.
- Añadir lote como icono dentro del banner CUSA.
- Inferencia de “FULLGAME/Game” como juego base cuando el SFO no trae categoría.
- Banner CUSA con etiquetas compactas Juego/Act./DLC y `FlowRow` para evitar que
  el DLC se corte.
- Confirmación Material 3 para eliminar de cola o biblioteca.
- Créditos a DeepSeek v4.1 flash por el análisis REA, ingeniería inversa y
  generación del clon Android.
