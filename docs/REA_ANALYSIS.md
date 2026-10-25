# Análisis REA: PS4 Remote PKG Sender V2 (Electron)

Fecha del análisis: 2026-10-24  
Fuente analizada: `/home/dilsonzm/Documentos/Proyectos/RemoteSend/ps4-remote-pkg-sender`  
Versión: `2.10.4` (`package.json`)

## 0. Metodología y herramientas

- Repositorio fuente: `Gkiokan/ps4-remote-pkg-sender` v2.10.4.
- Análisis REA sobre: `ps4.js`, `ps5.js`, `ps4_goldhen.js`, `fs.js`,
  `store/modules/*`, `pages/Index.vue`, `pages/app/Server.vue` y componentes de
  configuración/cola.
- Modelo de IA: **DeepSeek v4.1 flash**, usado para la ingeniería inversa, el
  análisis de protocolos y la generación/asistencia del clon Android.
- Validación con dispositivo real: RedMagic NX769J (Android 16) y PS4 con RPI en
  el puerto 12801.
- El clon no copia código del original: reimplementa el comportamiento en
  Kotlin/Compose y sustituye Express/Node por un servidor HTTP Android.

## 1. Resumen ejecutivo

La aplicación original es un cliente de escritorio Electron + Vue 2 que hace dos
cosas:

1. Publica archivos PKG locales mediante un servidor HTTP Express embebido.
2. Ordena a la consola que descargue e instale esos PKG usando APIs de homebrew:
   flatZ Remote Package Installer (RPI) en PS4, GoldHEN RPI en PS4 y etaHEN RPI
   en PS5.

El flujo relevante para Android no es el envío directo del archivo por socket,
sino un patrón **servidor de archivos + orden de instalación**:

```text
Teléfono/PC (servidor HTTP)  <--- GET PKG ---  PS4/PS5
        ^                                            |
        |          POST /api/install (URL)           |
        +--------------------------------------------+
```

La consola actúa como cliente HTTP y descarga el PKG desde el teléfono. Por eso
Android necesita un servidor HTTP local que permanezca activo en segundo plano,
además de los clientes de control de la consola.

## 2. Arquitectura del proyecto Electron

```text
src/main/                 Proceso principal Electron
  index.js                Ventanas, IPC, ciclo de vida
  helper.js               Creación de ventanas y descargas
  menu.js / tray.js       Menú y bandeja
src/renderer/             Proceso renderer (Vue 2 + Vuex + Element UI)
  plugins/
    ps4.js                Cliente HTTP flatZ RPI
    ps5.js                Cliente TCP etaHEN
    ps4_goldhen.js        Cliente TCP GoldHEN
    fs.js                 Escaneo de carpetas, PKG/SFO, nombres
    axios.js              Cliente HTTP compartido
  store/modules/
    app.js                Configuración de red, PS4, servidor y UI
    queue.js              Cola, tareas, instalados y logs
    server.js             Catálogo de archivos y estado del servidor
  pages/app/Server.vue    Servidor Express, rutas y heartbeat
  pages/Index.vue         Centro de procesamiento (cola e instalación)
  pages/Server.vue        Listado de PKG servidos
  components/ServerConfig.vue / PS4Config.vue / ...
```

Observaciones:

- El servidor Express se ejecuta en el **renderer**, no en el proceso main. Es
  una decisión propia de Electron que en Android no se puede replicar tal cual.
- La configuración no se persiste explícitamente en el código analizado; Vuex
  mantiene el estado en memoria. Android necesita persistencia real.
- El escaneo de archivos usa el sistema de archivos Node (`fs.readdirSync`).
  Android debe usar Storage Access Framework (SAF) y `ContentResolver`.
- El procesamiento de la cola y el polling de progreso viven en la UI. En Android
  se movieron a un servicio en primer plano y a un orquestador de aplicación.

## 3. Servidor HTTP local

Implementado en `src/renderer/pages/app/Server.vue`.

| Aspecto | Implementación Electron | Equivalente Android |
|---|---|---|
| Motor | Express 4 sobre Node | `ServerSocket` propio (HTTP/1.1) |
| Puerto por defecto | 8337 | 8337 configurable |
| Bind | `app.listen(port)` (todas las interfaces) | `0.0.0.0:port` |
| Heartbeat | `GET /hb` con IP/puerto remoto y mensaje | `GET /hb` con estado, paquetes y datos remotos |
| PKG | `GET /<patchedFilename>` con `res.download` | `GET /pkg/{id}/{safeName}` por streaming |
| Icono | `GET /<patchedFilename>/icon0.png` | `GET /icon/{id}` |
| Listado | No existe | `GET /api/packages` (añadido) |
| CORS | Cabeceras abiertas | Cabeceras abiertas |
| Range/HEAD | No implementado explícitamente | Implementado (`206`, `416`, `HEAD`) |
| Nombre servido | Nombre original saneado | `safeName` saneado |
| Prefijo de ruta | Opción `prependFullPath` | Ruta por ID opaca |

La URL se construye en `addFileEndpoint`:

```js
file.url = 'http://' + this.ip + ':' + this.port + '/' + file.patchedFilename
file.image = file.url + '/icon0.png'
```

El nombre se sanea en `plugins/fs.js` con:

```js
fileName.replace(/[^a-zA-Z0-9-_.]/g, '')
```

y opcionalmente se antepone la ruta completa.

## 4. API flatZ RPI (PS4)

Cliente: `src/renderer/plugins/ps4.js`.  
Base: `http://<IP_PS4>:12800`.  
Método: `POST` con cuerpo JSON (axios envía JSON aunque declare
`application/x-www-form-urlencoded`).

Endpoints identificados:

| Endpoint | Cuerpo | Uso |
|---|---|---|
| `/api/is_exists` | `{"title_id":"CUSA09311"}` | Comprobar instalación y tamaño |
| `/api/install` | `{"type":"direct","packages":["http://..."]}` | Instalar PKG |
| `/api/start_task` | `{"task_id":123}` | Iniciar tarea detenida |
| `/api/stop_task` | `{"task_id":123}` | Detener |
| `/api/pause_task` | `{"task_id":123}` | Pausar |
| `/api/resume_task` | `{"task_id":123}` | Reanudar |
| `/api/unregister_task` | `{"task_id":123}` | Eliminar tarea |
| `/api/get_task_progress` | `{"task_id":123}` | Progreso |
| `/api/find_task` | `{"content_id":"...","sub_type":6}` | Buscar tarea |
| `/api/uninstall_game` | `{"title_id":"..."}` | Desinstalar juego |
| `/api/uninstall_patch` | `{"title_id":"..."}` | Desinstalar parche |
| `/api/uninstall_ac` | `{"content_id":"..."}` | Desinstalar DLC |
| `/api/uninstall_theme` | `{"content_id":"..."}` | Desinstalar tema |

Respuesta de instalación correcta:

```json
{"status":"success","task_id":268435806,"title":"Tin & Kuna"}
```

Respuesta de error:

```json
{"status":"fail","error_code":2157510677}
```

Códigos documentados en el propio proyecto:

| Código | Interpretación del proyecto |
|---|---|
| `2157510681` | La tarea no existe |
| `2157510663` | Ya instalado (posible) |
| `2157510677` | Parece que ya está instalado |
| `2157510789` | No hay espacio suficiente |

Progreso (`get_task_progress`) devuelve campos como:

```json
{
  "status": "success",
  "length": 2667446272,
  "transferred": 236060672,
  "length_total": 2667446272,
  "transferred_total": 236060672,
  "rest_sec": 1116,
  "rest_sec_total": 1116,
  "preparing_percent": 100,
  "local_copy_percent": 0
}
```

Fórmula usada en Electron:

```text
percent = round(transferred / length * 100)
```

La app Android prefiere `transferred_total / length_total` y usa los campos
`length/transferred` como respaldo, además de detectar preparación y
finalización.

## 5. API TCP de PS5 etaHEN y PS4 GoldHEN

Clientes: `src/renderer/plugins/ps5.js` y `ps4_goldhen.js`.  
Transporte: `net.Socket` TCP a `<IP>:9090`.  
Formato: una única cadena JSON, respuesta JSON.

PS5 etaHEN:

```json
{"url":"http://<telefono>:8337/pkg/<id>/<archivo>.pkg"}
```

Respuesta:

```json
{"res":0}
```

Códigos observados:

| Código | Interpretación |
|---|---|
| `0` | Petición aceptada |
| `-2135809020` | El PKG no se pudo descargar (404) |

PS4 GoldHEN (RPI embebido):

```json
{
  "id": "<CONTENT_ID>",
  "contentUrl": "http://<telefono>:8337/pkg/<id>/<archivo>.pkg",
  "iconPath": "http://<telefono>:8337/icon/<id>",
  "contentName": "<TITLE>"
}
```

También responde `{"res":0}`.

Comprobación de conexión en Electron: abrir el socket, cerrarlo sin enviar datos
y considerar éxito si conecta. En Android se hace lo mismo, con timeout
configurable.

## 6. Cola, estados y automatización

El estado Vuex (`store/modules/queue.js`) guarda:

- `queue`: archivos en cola.
- `tasks`: respuestas de tareas.
- `installed`: historial.
- `logs`.

Estados usados en la UI original: `n/a`, `serving`, `in queue`, `installing`,
`pause`, `stop`, `finish`, `installed`, `installed +`, `exists`, `Sent to PS5`.

Flujo de instalación:

1. Usuario añade un PKG a la cola (estado `in queue`).
2. Pulsa iniciar.
3. Electron llama `/api/install` con la URL del servidor local.
4. Guarda `task_id`, estado `installing`.
5. Cada `ps4.update` ms llama `get_task_progress`.
6. Si termina, pasa a `finish` e `installed`.
7. Si `enableQueueScanner`, busca el siguiente `in queue` y lo inicia.

Para PS5, el original no tiene progreso ni pausa: envía la petición y espera la
respuesta. La cola PS5 usa un envío masivo con retardos de 2 s.

La app Android normaliza los estados a:

```text
QUEUED, INSTALLING, PAUSED, STOPPED, COMPLETED, SENT, FAILED, EXISTS
```

y conserva el auto-avance y el envío secuencial.

## 7. Escaneo, metadatos y nombres

`plugins/fs.js`:

- Escanea la carpeta base y, opcionalmente, subcarpetas.
- Filtra extensiones que contienen `pkg`.
- Extrae `CUSA\d{5}` del nombre como Title ID.
- Opcionalmente usa `@njzy/ps4-pkg-info` para leer `PARAM.SFO` e `icon0`.

El parser de `@njzy/ps4-pkg-info` revela el formato mínimo necesario:

- Cabecera PKG de `0x20` bytes con magia `\x7FCNT`.
- Número de entradas de tabla en offset `0x10` (big-endian).
- Offset de la tabla en `0x18` (big-endian).
- Entradas de 32 bytes; `id=0x1000` es `PARAM.SFO`; `id=0x1200` es `icon0`.
- `PARAM.SFO` little-endian: header de 20 bytes, entradas de 16 bytes, tabla de
  claves y tabla de datos. Formatos `0x0204` (string) y `0x0404` (entero).

La implementación Android (`data/pkg/PkgMetadataReader.kt`) replica este parser
sin dependencias nativas y guarda el icono en `filesDir/pkg_icons/<id>.png`.

## 8. Configuración de red

Valores originales (`store/modules/app.js`):

| Valor | Por defecto |
|---|---|
| IP del servidor | interfaz local seleccionada |
| Puerto del servidor | 8337 |
| IP de consola | vacío |
| Puerto RPI | 12800 |
| Puerto etaHEN/GoldHEN | 9090 |
| Puerto FTP | 2121 |
| Timeout | 2500 ms |
| Intervalo de progreso | 2200 ms |

Android mantiene estos valores y añade:

- Escaneo de `/24` con sockets para detectar consolas.
- Selección de interfaz local entre las direcciones IPv4 disponibles.
- Preferencia para interpretar el puerto 9090 como PS5 o PS4.
- Detección de IP no disponible y mensajes de diagnóstico.

## 9. Diferencias y desafíos de Android

| Desafío | Decisión Android |
|---|---|
| El renderer de Electron puede usar Node y Express | Servidor HTTP propio sobre `ServerSocket` |
| Acceso directo a rutas del sistema | SAF (`ACTION_OPEN_DOCUMENT` / `OPEN_DOCUMENT_TREE`) |
| Los URI de SAF no son rutas | `ContentResolver` + `ParcelFileDescriptor` y streaming |
| El proceso de UI muere en segundo plano | `ForegroundService` tipo `dataSync` + wake lock |
| No hay bandeja del sistema | Notificaciones y canal de servicio |
| No hay `prependFullPath` necesario | Ruta opaca por ID y nombre saneado |
| No hay file picker de carpetas profundo | `DocumentFile` recursivo con límite de 10 000 archivos |
| Detección de consola ambigua en 9090 | Detección RPI primero; preferencia PS5/PS4 y selector manual |
| PS5 sin progreso | Estado `SENT` y auto-avance |
| Persistencia inexistente en el original | `DataStore` con JSON versionado |

## 10. Conclusión

El comportamiento esencial que debe conservar Android es:

- Servir el PKG por HTTP con una URL accesible desde la consola.
- Ordenar la instalación por HTTP (PS4 RPI) o TCP/JSON (PS5/GoldHEN).
- Mantener una cola persistente, progreso y auto-avance.
- Gestionar los errores de red y códigos de la consola con mensajes claros.

La implementación Android conserva ese flujo y sustituye las dependencias de
escritorio por APIs móviles, añadiendo persistencia, servicio en segundo plano,
soporte de rangos HTTP y una interfaz Material 3.
