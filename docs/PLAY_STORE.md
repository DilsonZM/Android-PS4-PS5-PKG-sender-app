# Materiales para Play Store

## Recomendación de distribución

**No se recomienda publicar esta app en Google Play** en su estado actual:

- Utiliza `MANAGE_EXTERNAL_STORAGE`, permiso restringido que requiere
  justificación y revisión manual.
- Depende de homebrew de PS4/PS5 y de marcas de Sony.
- La app está pensada para sideload/GitHub Releases.

La vía recomendada es **GitHub Releases** con el APK firmado (RemoteSend.apk) y estas notas.

## Ficha corta

**RemoteSend: PKG a PS4/PS5**

Envía tus archivos PKG desde el teléfono a tu PS4 o PS5 por la red local, con
cola, progreso, velocidad, portadas y agrupación por CUSA.

## Descripción larga

RemoteSend convierte tu teléfono Android en un servidor de paquetes PKG para
PS4 y PS5. Selecciona tus archivos o escanea la carpeta Download, añádelos a la
cola y deja que la consola los descargue e instale por la red local.

Características:

- Compatible con PS4 flatZ RPI, PS4 GoldHEN y PS5 etaHEN.
- Detección automática y escaneo de la red local (IPv4).
- Cola persistente con pausa, reanudación, parada y auto-avance.
- Progreso real con porcentaje, MB/s, bytes transferidos y tiempo restante.
- Agrupación por CUSA: juego base, actualizaciones y DLC.
- Carátula del juego en la cola.
- Servidor HTTP embebido con heartbeat y soporte de rangos.
- Tema claro/oscuro, color dinámico y 60/120 FPS.
- Copia de seguridad/restauración de la cola en JSON.
- Registros y modo desarrollador.

Requisitos:

- Android 7 o superior.
- Teléfono y consola en la misma red.
- PS4 con RPI/GoldHEN o PS5 con etaHEN.
- El router no debe aislar clientes.

Aviso: herramienta no oficial, no afiliada a Sony. PlayStation, PS4 y PS5 son
marcas de Sony Interactive Entertainment.

## Texto promocional corto

“Tus PKG en la PS4/PS5 desde el móvil: cola, carátulas, progreso real y
velocidad.”

## Capturas sugeridas

1. Inicio con servidor en línea y consola conectada.
2. Consola con resultado del escaneo (IP y puertos).
3. Paquetes agrupados por CUSA con “Juego base” y “Actualización”.
4. Cola con carátula, CUSA y versión.
5. Cola con progreso: 32 %, 10,9 MB/s, ETA.
6. Ajustes con 60/120 FPS.

Archivos disponibles en `docs/screenshots/`.

## Desarrollador

**Dilson Zuleta Molina (DilsonZM)**  
Ingeniero Electrónico · Visual Analytics & Big Data  
Power Platform (Power BI, Power Apps, Power Automate), Python y SQL.  
GitHub: https://github.com/DilsonZM

## Clasificación y privacidad

- Categoría: Herramientas / Utilidades.
- Contenido: no contiene anuncios ni compras.
- Privacidad: no recopila datos; todo queda en la LAN y en el almacenamiento
  local. El servidor no se expone a Internet.
- Donaciones: enlace externo de PayPal (https://paypal.me/dilsonzuleta); no se
  usa Google Play Billing.
- Permisos destacados:
  - `INTERNET`: comunicación local.
  - `MANAGE_EXTERNAL_STORAGE` (opcional): escanear la carpeta Download.
  - `POST_NOTIFICATIONS`: estado de instalación.
  - Servicio en primer plano: mantener el servidor activo.

## Checklist de release

- [ ] Firmar APK/AAB con keystore de release.
- [ ] Probar en Android 10, 12, 14 y 16.
- [ ] Verificar política de `MANAGE_EXTERNAL_STORAGE` si se publica en Play.
- [ ] Añadir política de privacidad pública.
- [ ] Capturas en español e inglés.
- [ ] Probar con PS4 RPI, GoldHEN y PS5 etaHEN.
- [ ] Comprobar que no se incluyen marcas/logos de Sony sin permiso.
