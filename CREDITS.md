# Créditos

## Autor y mantenimiento

**Dilson Zuleta Molina (DilsonZM)** — Ingeniero Electrónico y especialista en
Visual Analytics & Big Data. Experto en Power Platform (Power BI, Power Apps,
Power Automate), Python y SQL. Más de 6 años liderando soluciones de analítica,
automatización y gestión de datos.

- GitHub: https://github.com/DilsonZM
- Proyecto: RemoteSend Android

## Apoyar

Si quieres apoyar el mantenimiento, puedes invitar un café por PayPal:

https://paypal.me/dilsonzuleta

## Proyecto original

- **PS4 Remote PKG Sender V2** — Gkiokan  
  https://github.com/Gkiokan/ps4-remote-pkg-sender  
  Versión analizada: `2.10.4` (Electron + Express + Vue 2).

Este proyecto Android es una reimplementación/clonación funcional basada en el
análisis REA de ese repositorio. El código original no se copió: se estudiaron
sus rutas, protocolos, estados, cola, servidor HTTP y clientes de consola, y se
implementó una versión nativa para Android.

## Análisis, ingeniería inversa y generación de código

- **DeepSeek v4.1 flash** — modelo de IA utilizado para el análisis REA, la
  ingeniería inversa del proyecto Electron, la definición de la arquitectura
  Android y la generación/asistencia del código.
- **opencode** — entorno de agente usado para ejecutar el análisis, editar el
  proyecto, compilar, probar en dispositivo real y documentar.

## App homebrew instalada en la PS4

- **njzydark / PS4RPI** — port OOSDK de flatZ RPI con puerto **12801** por
  defecto. Es la app que se ejecuta en la consola para recibir las órdenes de
  RemoteSend.  
  https://github.com/njzydark/PS4RPI
- **Backporter / ps4_remote_pkg_installer-OOSDK** — base del fork OOSDK.  
  https://github.com/Backporter/ps4_remote_pkg_installer-OOSDK

## Comunidad homebrew

- **flatz** — Remote Package Installer (RPI) original para PS4 (puerto 12800).
- **Specter**, **xvortex** y colaboradores de GoldHEN.
- Comunidad de etaHEN/PS5.
- Todos los testers y usuarios que reportaron comportamiento real de las APIs.

## Tecnologías Android

- Kotlin, Jetpack Compose, Material Design 3.
- AndroidX Lifecycle, Navigation, DataStore, DocumentFile.
- OkHttp.
- kotlinx.coroutines y kotlinx.serialization.

## Aviso

PlayStation, PS4 y PS5 son marcas registradas de Sony Interactive Entertainment.
Este proyecto no está afiliado ni respaldado por Sony. Es una herramienta
educativa/homebrew para redes locales de confianza.
