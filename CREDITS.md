# Créditos

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

## Comunidad homebrew

- **flatz** — Remote Package Installer (RPI) para PS4.
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
