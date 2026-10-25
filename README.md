# RemoteSend Android

Aplicación Android nativa para enviar archivos **PKG** desde el teléfono a una
**PS4** o **PS5** en la misma red local. Es una reimplementación de
[PS4 Remote PKG Sender V2](https://github.com/Gkiokan/ps4-remote-pkg-sender) con
Kotlin, Jetpack Compose y Material Design 3.

## Para quién es y por qué

RemoteSend nace para **personas que no tienen PC** y necesitan enviar sus
copias de seguridad/paquetes PKG a una PS4 o PS5. Tradicionalmente estas
herramientas dependen de un ordenador que ejecute Node/Express y sirva los
archivos por red; RemoteSend traslada todo al teléfono:

- El teléfono es el **servidor HTTP**, el **catálogo** y el **controlador**.
- No hace falta instalar Node, Express, Python ni cables.
- Los PKG se quedan en el almacenamiento local del teléfono.
- La consola descarga directamente desde el teléfono en la red local.
- La cola permite preparar juego base, actualizaciones y DLC y dejarlos
  instalándose en orden mientras el usuario hace otra cosa.

La motivación es práctica: facilitar el acceso a las copias de seguridad propias
en entornos donde el PC no está disponible, manteniendo todo en la LAN y sin
subir archivos a servicios externos.

## Estado de validación

Probado en un **RedMagic NX769J (Android 16)** contra una **PS4 con flatZ RPI en
el puerto 12801**:

- Escaneo de red encontró la PS4 en `192.168.18.12:12801`.
- Importación automática de **10 PKG** desde `Download`.
- Instalación real de *Totally Accurate Battle Simulator* (CUSA29952) de 1,93 GB.
- Progreso 0 → 100 %, velocidad real (~11 MB/s), bytes transferidos y ETA.
- La app permaneció estable durante la descarga y la instalación.

## Características

- Biblioteca local de PKG mediante Storage Access Framework y, opcionalmente,
  acceso a todos los archivos para escanear `Download` automáticamente.
- Auto-escaneo al iniciar, icono de re-escaneo en la barra superior y carpeta
  vinculada persistente.
- Lectura de metadatos fPKG: `TITLE`, `TITLE_ID`, `CONTENT_ID`, `APP_VER`,
  `CATEGORY` e icono `icon0`.
- Agrupación por **CUSA/Title ID**: juego base, actualizaciones y DLC en un mismo
  contenedor, con icono para añadir todo el lote a la cola y contraer/expandir
  global persistente.
- Carátula del juego visible en la cola y en la tarjeta de instalación activa.
- Eliminación desde la biblioteca con confirmación Material 3 y borrado real del
  archivo del almacenamiento.
- Buscador tipo píldora, iconos de agrupación y acciones compactas.
- Servidor HTTP embebido con heartbeat, listado JSON, `HEAD` y `Range`.
- Cola persistente con reordenación, instalación, pausa, reanudación, parada y
  eliminación.
- Progreso con **porcentaje, bytes transferidos/total, velocidad y tiempo
  restante**. El parser entiende los contadores hexadecimales que devuelve RPI
  (`0x...`).
- Auto-avance opcional de la cola.
- Cliente **PS4 flatZ RPI**, **PS4 GoldHEN** y **PS5 etaHEN**.
- Detección automática de consola y **escaneo de red** usando los puertos
  configurados y los estándar **12800 (flatZ)** / **12801 (PS4RPI)** / 9090.
- Servicio en primer plano para mantener el servidor y la cola activos.
- Notificaciones de progreso, finalización y error.
- Tema claro/oscuro, color dinámico y selector de **60/120 FPS** en Ajustes.
- Copia de seguridad/restauración de la cola en JSON mediante SAF.
- Registros, modo desarrollador y exportación de logs.

## Requisitos

- Android 7.0 (API 24) o superior.
- Teléfono y consola en la misma red local.
- El router no debe tener aislamiento de clientes (AP isolation).
- PS4: flatZ Remote Package Installer (12800), **PS4RPI de njzydark** (12801)
  o GoldHEN RPI (9090).
- PS5: etaHEN con RPI habilitado (9090).
- Compilar: JDK 17 o 21, Android SDK 36 y Gradle Wrapper incluido.

## Compilar

```bash
./gradlew :app:assembleDebug
```

APK:

```text
app/build/outputs/apk/debug/RemoteSend.apk
```

Pruebas y lint:

```bash
./gradlew :app:testDebugUnitTest :app:lintDebug
```

La suite actual contiene **55 pruebas** unitarias/de integración local.

## Instalar

USB:

```bash
adb install -r app/build/outputs/apk/debug/RemoteSend.apk
adb shell am start -n com.remotesend.app/.MainActivity
```

Depuración inalámbrica:

```bash
adb pair 192.168.18.119:<puerto-de-emparejamiento>
adb connect 192.168.18.119:33275
scripts/install-wireless.sh 192.168.18.119:33275
```

## Uso rápido

1. Abre RemoteSend y concede notificaciones.
2. En **Consola**, escribe la IP (o pulsa **Escanear red**).
3. Ajusta el puerto RPI/TCP; guarda.
4. Pulsa **Comprobar**; el icono de estado se pone verde y la pestaña Consola
   muestra un punto verde.
5. Inicia el **Servidor local** (puede auto-iniciarse).
6. En **Paquetes**, pulsa **Sincronizar** o concede acceso a todos los archivos
   desde Consola para escanear `Download` automáticamente.
7. Añade un PKG o usa el icono de lote del banner CUSA para encolar todo el
   grupo; pulsa **Iniciar**.
8. Con **Auto-avanzar** activo, al terminar el juego base continúa la
   actualización y después el DLC.

### Escanear red

El botón **Escanear red** recorre la subred `/24` (253 hosts, excluyendo el
teléfono) y prueba:

- El puerto RPI configurado (por ejemplo `12801`) y el estándar `12800`.
- El puerto TCP configurado y el estándar `9090`.

Los resultados muestran IP, tipo de consola y puertos. Al tocar uno se rellenan
la IP y el puerto automáticamente. Si no encuentra nada, revisa que el RPI esté
activo, que la consola esté en la misma red y que el router no aísle clientes.

## Rendimiento de red

El servidor del teléfono puede entregar más de **20 MB/s** a otro cliente de la
red. En la prueba real la PS4 recibió alrededor de **11 MB/s** (~88 Mbps), lo
que suele indicar un enlace Ethernet de 100 Mbps, un puerto de router limitado o
el propio límite del RPI. Para intentar superarlo:

- Comprueba en la PS4 el estado de conexión (100 Mbps vs 1 Gbps).
- Usa un puerto Gigabit y cable Cat5e/Cat6.
- Conecta el teléfono por USB-C Ethernet si el dispositivo lo permite y elige
  esa interfaz en **Consola**.
- Si la PS4 está a 1 Gbps y sigue en ~11 MB/s, el límite es del RPI/consola y no
  de la app.

## Estructura

```text
app/src/main/java/com/remotesend/app/
  core/              red, almacenamiento y logging
  data/
    backup/          exportación/importación JSON
    install/         cola, progreso, pausa/stop y auto-avance
    log/             logger persistente
    network/         PS4 RPI, TCP PS5/GoldHEN, detección y escaneo LAN
    persist/         DataStore JSON
    pkg/             lector PKG/PARAM.SFO y fuentes seekable
    repository/      catálogo, cola, ajustes e importación SAF/ficheros
    server/          servidor HTTP embebido
    service/         foreground service y notificaciones
  di/                contenedor manual de dependencias
  domain/            modelos y utilidades
  ui/                Compose Material 3
docs/                análisis, API, arquitectura, guía, testing y Play Store
```

## Capturas

- `docs/screenshots/01-home.png`
- `docs/screenshots/03-console-connected.png`
- `docs/screenshots/04-queue-artwork.png`
- `docs/screenshots/05-queue-progress.png`
- `docs/screenshots/06-home-progress.png`
- `docs/screenshots/07-scan-result.png`
- `docs/screenshots/08-packages-grouped.png`
- `docs/screenshots/09-packages-collapsed.png`
- `docs/screenshots/10-delete-dialog.png`
- `docs/screenshots/11-queue-remove-dialog.png`
- `docs/screenshots/12-creator.png`
- `docs/screenshots/13-home-coffee.png`

## Firma de releases

A partir de **v1.0.3** los Releases se firman con una keystore estable en GitHub
Actions, de modo que las actualizaciones se instalan encima sin desinstalar.
La keystore no se guarda en el repositorio: se configura con los secretos
`ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS` y
`ANDROID_KEY_PASSWORD`. Instrucciones: [docs/SIGNING.md](docs/SIGNING.md).

## Apoyar el proyecto

Si RemoteSend te resulta útil, puedes invitar un café por PayPal desde el icono
de taza de la barra superior o desde **Ajustes → Creador**:

https://paypal.me/dilsonzuleta

La donación es voluntaria y no desbloquea funciones.

## Autor

**Dilson Zuleta Molina (DilsonZM)** — Ingeniero Electrónico y especialista en
Visual Analytics & Big Data. Experto en Power Platform (Power BI, Power Apps,
Power Automate), Python y SQL. Más de 6 años liderando soluciones de analítica,
automatización y gestión de datos.

- GitHub: https://github.com/DilsonZM

## Documentación

- [Análisis REA](docs/REA_ANALYSIS.md)
- [API y protocolos](docs/API.md)
- [Arquitectura](docs/ARCHITECTURE.md)
- [Guía de usuario](docs/USER_GUIDE.md)
- [Testing](docs/TESTING.md)
- [Play Store](docs/PLAY_STORE.md)
- [Historia y motivación](docs/PROJECT_STORY.md)

## Limitaciones

- El auto-escaneo de la raíz `Download` requiere el permiso especial
  `MANAGE_EXTERNAL_STORAGE`; sin él se usa SAF y se pueden importar archivos o
  subcarpetas seleccionadas manualmente.
- El puerto 9090 no permite distinguir por sí solo entre PS4 GoldHEN y PS5
  etaHEN; se usa una preferencia y se puede cambiar manualmente.
- PS5 etaHEN no expone progreso; el elemento queda como **Enviado**.
- La detección y el escaneo solo cubren IPv4 en la subred local.
- El servidor usa HTTP sin cifrar en la LAN. No lo expongas a Internet.
- Algunos fabricantes limitan servicios en segundo plano; mantén la
  notificación del servicio activa.

## Créditos y aviso legal

Inspirado en PS4 Remote PKG Sender V2 de Gkiokan y en el trabajo de flatz,
Specter, xvortex y la comunidad homebrew. La app homebrew que se instala en la
PS4 es **PS4RPI** de **njzydark** (port OOSDK de flatZ RPI, puerto 12801 por
defecto), fork de Backporter/ps4_remote_pkg_installer-OOSDK. PlayStation, PS4 y
PS5 son marcas de Sony Interactive Entertainment. Este proyecto no está afiliado
a Sony.

Licencia MIT. Consulta [LICENSE](LICENSE). Créditos completos, incluido el
modelo **DeepSeek v4.1 flash** usado para el análisis REA/ingeniería inversa y
la clonación, en [CREDITS.md](CREDITS.md).
