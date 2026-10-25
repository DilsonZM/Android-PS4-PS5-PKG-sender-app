# Historia, motivación y solución

## El problema

Enviar PKG a una PS4/PS5 con homebrew suele requerir un PC que actúe como
servidor. El PC ejecuta Node/Express, publica la carpeta de PKG por HTTP y desde
la consola se instala usando flatZ RPI, GoldHEN o etaHEN. Para muchas personas
eso es una barrera: no tienen PC, no quieren instalarlo, o el PC no está cerca
del router.

## La idea

RemoteSend traslada ese servidor al teléfono. El teléfono ya está en la red, ya
tiene los archivos descargados y puede permanecer encendido mientras la consola
descarga. La app:

1. Escanea el almacenamiento y extrae metadatos (título, CUSA, Content ID,
   versión, categoría e icono).
2. Agrupa los PKG del mismo CUSA: juego base, actualizaciones y DLC.
3. Publica los archivos con un servidor HTTP embebido.
4. Ordena a la consola que descargue e instale mediante RPI, GoldHEN o etaHEN.
5. Mantiene una cola persistente y muestra progreso, velocidad, bytes y ETA.
6. Si el usuario lo activa, continúa automáticamente con el siguiente PKG del
   lote: base → actualización → DLC.

## Por qué es una solución útil

- No requiere PC, Node, Express ni conocimientos de red.
- No sube PKG a Internet: todo queda en la LAN.
- Funciona con el almacenamiento que el usuario ya tiene en el teléfono.
- Permite preparar un lote completo y dejarlo instalándose.
- Da visibilidad real del estado y de los errores de la consola.
- Es útil para personas que descargan sus copias de seguridad en el móvil y
  quieren pasarlas a la consola sin depender de un ordenador.

## Cómo se construyó

El punto de partida fue el proyecto Electron **PS4 Remote PKG Sender V2** de
Gkiokan (v2.10.4). Se hizo un análisis REA de su servidor Express, sus clientes
PS4/PS5, su cola y sus estados. A partir de ese comportamiento se diseñó un clon
Android nativo:

- Servidor HTTP propio sobre `ServerSocket`.
- Importación SAF y escaneo directo de ficheros.
- Parser PKG/PARAM.SFO y extracción de `icon0`.
- Cliente OkHttp para RPI y socket TCP para GoldHEN/etaHEN.
- Progreso que entiende los contadores hexadecimales de RPI.
- Servicio foreground, wake lock y notificaciones.
- UI Compose Material 3 con agrupación, carátulas, confirmaciones y ajustes.
- Persistencia DataStore y backups JSON.

El análisis, la ingeniería inversa y la generación del clon se asistieron con
**DeepSeek v4.1 flash** dentro de opencode. El resultado se validó en un
RedMagic NX769J (Android 16) contra una PS4 real con RPI en el puerto 12801:
se importaron 10 PKG, se instaló un juego de 1,93 GB y después un lote completo
base → actualización → DLC de forma automática.

## Principios

- Local primero: nada sale de la red doméstica.
- Sin dependencias de escritorio.
- Confirmaciones claras antes de borrar.
- Progreso honesto: si la consola no informa, se muestra “Enviado”, no se
  inventa un porcentaje.
- Documentar límites: el enlace de la consola/RPI puede limitar a ~100 Mbps
  aunque el teléfono pueda más.
