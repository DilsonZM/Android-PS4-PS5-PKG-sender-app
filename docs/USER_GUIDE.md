# Guía de usuario

## 1. Preparar la consola

### PS4

1. Inicia **flatZ Remote Package Installer** (RPI) o **GoldHEN** con RPI.
2. Comprueba el puerto: normalmente `12800` (flatZ) o `9090` (GoldHEN).
3. Conecta la PS4 por LAN a un puerto Gigabit si es posible.
4. En **Ajustes → Red → Ver estado de conexión** revisa la velocidad de enlace.

### PS5

1. Activa **etaHEN** y su RPI.
2. El puerto habitual es `9090`.

## 2. Preparar el teléfono

1. Instala el APK.
2. Abre RemoteSend y concede notificaciones.
3. Conéctate a la misma red que la consola (mejor 5 GHz/6 GHz).
4. Opcional: en **Consola** concede **acceso a todos los archivos** para
   auto-escanear `Download`.

## 3. Configurar la consola

Puedes escribir la IP o usar **Escanear red**.

- **Escanear red** recorre 253 hosts de la subred y prueba el puerto RPI y TCP
  configurados, además de 12800/9090.
- Si encuentra la consola, aparecerá una tarjeta con IP y puertos. Tócala para
  rellenar el formulario.
- `253 de 253` significa que terminó de revisar todos los hosts. No es un error.
- Si no encuentra nada: revisa RPI/etaHEN, la misma red y el aislamiento de
  clientes del router.

Después pulsa **Comprobar**. El icono de estado y el punto verde de la pestaña
**Consola** indican conexión.

Guarda con el icono de disco.

## 4. Importar PKG

### Auto-escaneo (recomendado)

1. Concede acceso a todos los archivos.
2. La ruta por defecto es `/storage/emulated/0/Download`.
3. La app escanea al iniciar; el icono de refresco de la barra superior fuerza
   una sincronización.
4. Los PKG nuevos aparecen automáticamente en **Paquetes**.

### SAF

- **Añadir PKG**: selecciona archivos.
- **Añadir carpeta**: selecciona una carpeta que Android permita.
- La raíz `Download` no se puede elegir en Android 11+; para eso está el acceso
  total o una subcarpeta.

## 5. Paquetes y grupos CUSA

Cada grupo reúne los paquetes del mismo Title ID:

```text
CUSA29952 · 2 paquete(s) · 2,77 GB
[Juego base] [Actualización]
  ├─ Juego base   v01.00  1,93 GB
  └─ Actualización v01.06  864 MB
```

- **Juego base**: paquete principal.
- **Actualización**: parche.
- **DLC**: contenido adicional.
- Si el grupo tiene los tres tipos, el banner usa etiquetas compactas
  **Juego / Act. / DLC** para que el DLC siempre sea visible; si solo hay base
  se muestra **Juego base**, y si hay base + actualización se muestran ambas.
- El icono de capas de la barra superior activa/desactiva la agrupación.
- El icono de lote dentro del banner CUSA encola base, actualizaciones y DLC en
  orden.
- El icono de contraer/expandir junto a la carpeta vinculada aplica el estado a
  todos los CUSA y se recuerda entre sesiones.
- La búsqueda tipo píldora acepta título, `CUSA...` o Content ID.
- Eliminar desde el menú de un paquete pide confirmación y borra el archivo del
  almacenamiento. Quitar de la cola solo quita la entrada, no borra el PKG.

## 6. Cola

- Añade PKG con el botón **+**.
- Reordena con flechas.
- **Iniciar**: comienza la instalación.
- **Pausar** / **Reanudar**: solo con PS4 flatZ RPI.
- **Detener**: cancela la tarea.
- **Auto-avanzar**: instala el siguiente al terminar. Validado con un lote
  CUSA54308: base → actualización → DLC, todos completados.
- Cada tarjeta muestra carátula, categoría, CUSA, versión, tamaño, estado y
  controles.

Durante la instalación verás:

```text
32 % · 10,9 MB/s                         Quedan 2m 5s
648 MB de 1,93 GB
```

- **%**: progreso real.
- **MB/s**: velocidad calculada entre muestras.
- **Quedan**: ETA del RPI (o calculada).
- **Task ID**: identificador de la tarea.

## 7. Ajustes

- **Tema**: sistema/claro/oscuro.
- **Color dinámico**: Material You (Android 12+).
- **Frecuencia**: Automático / 60 FPS / 120 FPS. Solicita el modo de pantalla
  más cercano; depende del panel y consume más batería.
- **Notificaciones**: progreso y resultado.
- **Copias de seguridad**: exportar/importar cola JSON.
- **Modo desarrollador** y **Registros**.

## 8. Rendimiento y red

En la prueba con PS4 por LAN el servidor del teléfono entregó ~22 MB/s a una
laptop, pero la PS4 recibió ~11 MB/s. Ese límite suele ser:

1. Enlace Ethernet de la PS4 a 100 Mbps.
2. Puerto del router a 100 Mbps o cable deficiente.
3. Límite del RPI/consola.

Qué probar:

- Estado de conexión de la PS4: si dice 100 Mbps, cambia de puerto/cable.
- Usa Cat5e/Cat6 y un puerto Gigabit.
- Si puedes, conecta el teléfono por USB-C Ethernet y selecciona esa interfaz.
- Si la PS4 está a 1 Gbps y sigue en ~11 MB/s, es el RPI.

No se puede superar el límite del enlace/consola desde la app. El servidor
soporta más de 20 MB/s.

## 9. Solución de problemas

| Síntoma | Causa / solución |
|---|---|
| La app se cerraba al iniciar la instalación | Corregido: los cortes `Broken pipe` ya no tumban el proceso |
| No aparecen PKG nuevos | Pulsa **Sincronizar**; revisa la ruta y el acceso total |
| No se puede elegir Download con SAF | Usa acceso total o una subcarpeta |
| Escanear red encuentra 0 consolas | Usa el puerto correcto en el formulario; revisa RPI y AP isolation |
| La PS4 no descarga (404) | El teléfono y la consola deben verse; revisa IP/puerto del servidor |
| El puerto 8337 está ocupado | Cambia el puerto en Consola y reinicia el servidor |
| Progreso en 0 % | Actualiza a la versión con parser hexadecimal |
| Sin espacio | Código `2157510789`; libera espacio en la consola |
| Pantalla apagada corta la descarga | Mantén la notificación del servicio y desactiva optimizaciones del fabricante |

## 10. Capturas

- Inicio: `screenshots/01-home.png`
- Consola conectada: `screenshots/03-console-connected.png`
- Cola con carátula: `screenshots/04-queue-artwork.png`
- Progreso: `screenshots/05-queue-progress.png`
- Inicio durante instalación: `screenshots/06-home-progress.png`
- Resultado del escaneo: `screenshots/07-scan-result.png`
