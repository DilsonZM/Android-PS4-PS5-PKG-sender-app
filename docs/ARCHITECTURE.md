# Arquitectura Android

## 1. Capas

```text
UI (Compose)
  ├─ ViewModels (estado + eventos)
  ├─ LocalAppContainer (DI manual)
  └─ Servicio foreground
Data
  ├─ repositorios (paquetes, cola, ajustes)
  ├─ importadores SAF / ficheros
  ├─ servidor HTTP
  ├─ clientes PS4/PS5
  ├─ instalación y notificaciones
  └─ DataStore JSON
Domain
  ├─ modelos (PkgFile, QueueItem, ConsoleConfig, ...)
  └─ utilidades (nombres, categorías, cola)
```

No se usa Hilt para evitar complejidad de build. `AppContainer` crea y comparte
las dependencias; `RemoteSendApp` lo inicializa y `MainActivity` lo expone vía
`LocalAppContainer` a Compose.

## 2. Persistencia

`StateStore` envuelve un `DataStore<PersistedState>` con serializador JSON
(`kotlinx.serialization`).

```text
PersistedState
  schemaVersion
  packages: List<PkgFile>
  queue: List<QueueItem>
  console: ConsoleConfig
  server: ServerConfig
  settings: AppSettings
  logs: List<LogEntry> (máx. 600)
```

- `ignoreUnknownKeys` permite evolucionar el esquema.
- Las escrituras usan `updateData` (atómicas).
- Los campos nuevos tienen valores por defecto.
- Algunos productos no permiten acceso aleatorio; el lector PKG usa
  `ContentUriSeekableSource` y cae a un stream secuencial si hace falta.

## 3. Importación de PKG

### SAF

- Archivos: `ACTION_OPEN_DOCUMENT` múltiple.
- Carpetas: `ACTION_OPEN_DOCUMENT_TREE` + `DocumentFile` recursivo.
- Se persiste el permiso y la carpeta elegida (`lastScanTreeUri`).

### Ficheros directos (auto-escaneo de Download)

Android bloquea elegir la raíz `Download` con SAF. Para resolverlo:

- Se declara `MANAGE_EXTERNAL_STORAGE` (opcional).
- En Consola se muestra el estado y un botón para abrir los ajustes.
- Si está concedido, `PackageImporter.importFromPath()` recorre la ruta
  configurada (`/storage/emulated/0/Download` por defecto).
- Los paquetes se guardan con URI `file://`.
- `PackageRepository` y `ContentUriSeekableSource`/`FileSeekableSource` abren
  esos ficheros para metadatos y streaming.

### Metadatos

`PkgMetadataReader`:

1. Lee cabecera PKG (`\x7FCNT`), tabla (offset `0x10`/`0x18`).
2. Encuentra `PARAM.SFO` (`0x1000`) e `icon0` (`0x1200`).
3. Parsea SFO little-endian (`0x0204` string, `0x0404` entero).
4. Devuelve título, Title ID, Content ID, versión, categoría e icono.
5. Si falla, usa el nombre del archivo (`CUSA\d{5}`, Content ID) y la categoría
   se infiere de palabras como `Update`, `Patch`, `DLC`, `Optional`, `FULLGAME`
   o `Game`.

La importación está serializada con un `Mutex` para evitar que el auto-escaneo y
una importación manual escriban a la vez.

## 4. Servidor HTTP

`PkgHttpServer` es un servidor HTTP/1.1 mínimo sobre `ServerSocket`:

- Acepta conexiones en `Dispatchers.IO`.
- Un cliente por corrutina.
- Soporta `GET`, `HEAD`, `OPTIONS`, `Range`.
- Streaming desde `PackageRepository.openStreamAt()` sin cargar el PKG en RAM.
- Los errores `IOException` (por ejemplo `Broken pipe` cuando la consola corta
  la descarga) se capturan y se registran sin cerrar el proceso.
- `ServerManager` controla el ciclo de vida y publica `ServerRuntime`.

`RemoteSendService` mantiene el proceso en primer plano, adquiere un
`PARTIAL_WAKE_LOCK` y actualiza la notificación del servicio.

## 5. Red de consola

### PS4 RPI

`Ps4RpiClient` usa OkHttp:

- Un cliente rápido para comprobaciones.
- Un cliente extendido (15 s) para `/api/install`.
- `JsonSupport.longValue()` acepta enteros, decimales y **hexadecimal `0x...`**.

### PS5/GoldHEN

`TcpConsoleClient`:

- Conecta por socket, escribe JSON y lee hasta obtener JSON válido o EOF/timeout.
- PS5: `{"url": ...}`.
- GoldHEN: `id`, `contentUrl`, `iconPath`, `contentName`.
- Sin progreso ni pausa (la API no los expone).

### Detección y escaneo

`ConsoleDetector` publica un `StateFlow<ConsoleConnectionState>` compartido:

- La UI muestra icono verde y badge en la pestaña Consola.
- `UNKNOWN`, `CHECKING`, `CONNECTED`, `DISCONNECTED`.
- Cualquier comprobación o instalación actualiza el estado.

`LanScanner` usa corrutinas con semáforo (48 conexiones) y prueba los puertos
configurados y los estándar.

## 6. Cola e instalación

`InstallationManager` es un singleton de aplicación:

```text
start(itemId)
  ├─ valida servidor, PKG, URL y consola
  ├─ detecta tipo de consola
  ├─ si taskId existía y estaba pausado/detenido → resume
  ├─ llama install()
  ├─ guarda taskId/estado
  └─ pollTask():
       ├─ lee get_task_progress
       ├─ normaliza hex → bytes
       ├─ calcula velocidad entre muestras
       ├─ actualiza QueueItem y notificaciones
       ├─ Completed / TaskMissing → COMPLETED
       └─ 3 fallos consecutivos → FAILED
```

- Solo hay una instalación activa.
- Pausa/reanudación usan RPI; en TCP no están disponibles.
- Auto-avance arranca el siguiente `QUEUED` al terminar, con 1,2 s de margen.
- El servicio sigue activo mientras hay una instalación o el servidor está
  encendido.

## 7. UI

- Material 3, tema claro/oscuro y color dinámico.
- `MainActivity` solo observa `AppSettings` para evitar recomposiciones por logs
  o progreso.
- Ajustes de **60/120 FPS**: se solicita el modo de pantalla más cercano con
  `preferredDisplayModeId` y `preferredRefreshRate`.
- Paquetes: buscador tipo píldora, agrupación por CUSA activable con un icono en
  la barra superior, contraer/expandir global persistido en ajustes, pills de
  categoría, carátula y un icono de “añadir lote” dentro del banner CUSA.
- Eliminaciones con confirmación Material 3 (icono, título, texto y acción
  destructiva). Borrar un paquete elimina también el archivo del almacenamiento
  cuando Android lo permite; quitar de la cola no borra el archivo.
- Cola: carátula, categoría, versión, CUSA, progreso, velocidad, ETA, tarea y
  controles.
- Registros: filtros por nivel, búsqueda, expansión y exportación.
- Juegos instalados: `InstalledRepository` descarga
  `/system_data/priv/mms/app.db` por FTP de GoldHEN (2121), lee las tablas
  `tbl_appbrowse_*` (`category='gd'`), descarga iconos de
  `/user/appmeta/<TITLEID>/icon0.png` y los cachea. La biblioteca local enriquece
  actualizaciones/DLC. La pantalla muestra una dona de distribución, los juegos
  más pesados, buscador y lista A-Z. PS4RPI se usa solo para `uninstall_game`,
  `uninstall_patch` y `uninstall_ac`. Si FTP falla, hay fallback secuencial a
  `is_exists`. `is_exists` concurrente corrompe el `pool` estático de PS4RPI y
  provoca `CE-34878-0`; nunca se paraleliza.
- Navegación principal con `HorizontalPager` y barra inferior sincronizada.
- Ajustes: envío FTP de archivos a `/user/data/RemoteSend` sin instalarlos.
## 8. Permisos

| Permiso | Uso |
|---|---|
| `INTERNET` | Servidor y APIs |
| `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE` | Diagnóstico |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_DATA_SYNC` | Servicio |
| `POST_NOTIFICATIONS` | Progreso y estado |
| `WAKE_LOCK` | Mantener la descarga |
| `MANAGE_EXTERNAL_STORAGE` | Auto-escaneo de Download (opcional) |
| `READ_EXTERNAL_STORAGE` (≤32) | Compatibilidad |

## 9. Decisiones y límites

- HTTP plano por requisito de las consolas homebrew.
- Un solo servidor por app; el puerto se puede cambiar y reiniciar.
- El escaneo es activo (conexiones TCP), no mDNS.
- El progreso depende de los campos de RPI; el parser tolera hex y pares
  `length/transferred` o `*_total`.
- El límite de ~11 MB/s observado está en el enlace PS4/RPI, no en el servidor
  del teléfono (que entregó ~22 MB/s a la laptop).
