# API y protocolos

## 1. Servidor HTTP local

La app escucha en `0.0.0.0:<puerto>` (8337 por defecto) y publica la URL con la
IP de la interfaz seleccionada. La consola descarga desde ese servidor.

### `GET /hb` o `/heartbeat`

```json
{
  "status": "ok",
  "app": "RemoteSend",
  "message": "RemoteSend está sirviendo archivos PKG",
  "packages": 10,
  "remoteAddress": "192.168.18.12",
  "remotePort": 51234,
  "localAddress": "192.168.18.119",
  "localPort": 8337,
  "time": 1792897438921
}
```

### `GET /api/packages`

Lista los PKG con URL e icono:

```json
{
  "status": "ok",
  "count": 1,
  "packages": [
    {
      "id": "b6765bbc65ba32ff28d9df21",
      "name": "UP0001-CUSA09311_00-TEST.pkg",
      "safeName": "UP0001-CUSA09311_00-TEST.pkg",
      "title": "Test Game",
      "titleId": "CUSA09311",
      "size": 41233,
      "url": "http://192.168.18.119:8337/pkg/b6765bbc65ba32ff28d9df21/UP0001-CUSA09311_00-TEST.pkg",
      "iconUrl": "http://192.168.18.119:8337/icon/b6765bbc65ba32ff28d9df21"
    }
  ]
}
```

### `GET /pkg/{id}/{safeName}`

Sirve el PKG por streaming.

- `Content-Type: application/octet-stream`
- `Content-Length`
- `Accept-Ranges: bytes`
- `Content-Disposition: attachment; filename="..."`
- `Range: bytes=start-end` → `206 Partial Content`
- Rango inválido → `416 Range Not Satisfiable`
- `HEAD` devuelve cabeceras sin cuerpo.

### `GET /icon/{id}`

Devuelve `icon0` extraído del PKG como `image/png`, o `404`.

### `GET /`

Página HTML de diagnóstico con la lista de PKG y enlaces.

### CORS

Se permiten `GET`, `HEAD` y `OPTIONS` desde cualquier origen local.

## 2. flatZ RPI (PS4)

Base: `http://<IP>:<puerto>`.
- flatZ original: **12800**.
- **PS4RPI de njzydark** (port OOSDK, la app que se instala en la consola):
  **12801** por defecto.
- También se admite cualquier puerto configurado.

Todas las peticiones son `POST` con JSON. La detección automática y el escaneo
prueban 12800 y 12801.

| Endpoint | Cuerpo | Uso |
|---|---|---|
| `/api/is_exists` | `{"title_id":"CUSA29952"}` | Comprobar instalación |
| `/api/install` | `{"type":"direct","packages":["http://..."]}` | Instalar |
| `/api/start_task` | `{"task_id":441}` | Iniciar tarea |
| `/api/stop_task` | `{"task_id":441}` | Detener |
| `/api/pause_task` | `{"task_id":441}` | Pausar |
| `/api/resume_task` | `{"task_id":441}` | Reanudar |
| `/api/unregister_task` | `{"task_id":441}` | Eliminar tarea |
| `/api/get_task_progress` | `{"task_id":441}` | Progreso |
| `/api/find_task` | `{"content_id":"...","sub_type":6}` | Buscar tarea |
| `/api/uninstall_game` | `{"title_id":"CUSA29952"}` | Desinstalar |

Respuesta de instalación:

```json
{"status":"success","task_id":441,"title":"Totally Accurate Battle Simulator"}
```

### Progreso: los contadores son hexadecimales

La PS4 devuelve `length`, `transferred`, `length_total` y `transferred_total`
como cadenas hexadecimales:

```json
{
  "status": "success",
  "bits": "0x2412F98A",
  "error": 0,
  "length": "0x7B440000",
  "transferred": "0x29CE0000",
  "length_total": "0x7B440000",
  "transferred_total": "0x29CE0000",
  "num_index": 1,
  "num_total": 1,
  "rest_sec": 123,
  "rest_sec_total": 123,
  "preparing_percent": 100,
  "local_copy_percent": 100
}
```

Valores equivalentes:

- `0x7B440000` = `2068054016` bytes (1,93 GB)
- `0x29CE0000` = `701366272` bytes (~33,9 %)
- ETA: `rest_sec_total = 123` segundos

RemoteSend prioriza el mayor total disponible y el mayor transferido:

```text
total = max(length_total, length)
done  = max(transferred_total, transferred)
%     = done / total * 100
```

Además calcula la velocidad comparando `done` entre muestras:

```text
speed = (done_actual - done_anterior) / (t_actual - t_anterior)
```

La UI muestra porcentaje, `done / total`, velocidad en MB/s y ETA. Si no hay
`rest_sec`, la ETA se calcula con `(total - done) / speed`.

### Juegos instalados

La lista real de instalados se lee de `/system_data/priv/mms/app.db` por el FTP
de GoldHEN (2121, anónimo); PS4RPI no expone un listado global. La app muestra
tamaños de `app.db`, iconos de `/user/appmeta/<TITLEID>/icon0.png`, buscador y
lista A-Z. `is_exists` queda solo como fallback secuencial. Para desinstalar
envía:

- `/api/uninstall_game` con `title_id`
- `/api/uninstall_patch` con `title_id`
- `/api/uninstall_ac` con `content_id`

Limitaciones: RPI no expone espacio libre total ni confirma actualizaciones/DLC.
No hay selector de capacidad. `is_exists` no es seguro en paralelo (buffer
estático); la app lo llama de uno en uno. El FTP de GoldHEN debe estar activo en
el puerto 2121.

### Errores

Los códigos también pueden llegar en hexadecimal:

| Código | Significado |
|---|---|
| `2157510681` / `0x80990019` | La tarea no existe |
| `2157510663` | Ya instalado (posible) |
| `2157510677` | Ya instalado |
| `2157510789` | Sin espacio |
| `-2135809020` | El PKG no se pudo descargar (404) |

## 3. PS5 etaHEN y PS4 GoldHEN (TCP)

Socket TCP a `<IP>:9090`. Se envía un objeto JSON y se lee la respuesta JSON.

### PS5 etaHEN

```json
{"url":"http://192.168.18.119:8337/pkg/<id>/<archivo>.pkg"}
```

Respuesta:

```json
{"res":0}
```

### PS4 GoldHEN

```json
{
  "id": "UP3456-CUSA29952_00-1412781209801863",
  "contentUrl": "http://192.168.18.119:8337/pkg/<id>/<archivo>.pkg",
  "iconPath": "http://192.168.18.119:8337/icon/<id>",
  "contentName": "Totally Accurate Battle Simulator"
}
```

`id` requiere `CONTENT_ID`; si no se pudo leer el SFO se intenta derivar del
nombre. Si GoldHEN no devuelve `res`, se acepta `success=true`.

## 4. Detección y escaneo

`ConsoleDetector`:

1. Si el modo es explícito, prueba ese cliente.
2. En modo automático prueba flatZ RPI por HTTP.
3. Si falla, prueba el socket TCP.
4. El puerto 9090 es ambiguo: se usa `preferPs5ForTcp` o el modo manual.

`LanScanner`:

- Calcula la subred `/24` de la IP local.
- Genera 253 hosts (excluye el teléfono).
- Prueba el puerto RPI configurado y `12800`.
- Si no hay RPI, prueba el puerto TCP configurado y `9090`.
- Emite progreso y candidatos con IP, puertos y tipo sugerido.

## 5. Seguridad

- HTTP sin TLS porque las consolas homebrew no implementan TLS en este flujo.
- No hay autenticación; cualquiera en la LAN podría descargar los PKG mientras el
  servidor esté activo.
- No se envía telemetría ni datos a Internet.
- `MANAGE_EXTERNAL_STORAGE` es opcional y solo se usa para escanear carpetas.
- No se exponen los PKG fuera de la red local; no hacer port forwarding.
