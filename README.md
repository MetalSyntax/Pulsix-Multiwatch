# Daewoo DW26 — app propia

App Android (Kotlin) para el smartwatch Daewoo DW26 (firmware 1.01.00, plataforma IDO):
salud diaria, notificaciones, llamadas, música, clima, alarmas, esferas (catálogo + propias) y temas claro/oscuro/negro.
Usa el SDK de IDO que trae la app VeryFit (`com.ido.ble`) y su librería nativa de protocolo.

> **Proyecto no oficial.** No está afiliado, respaldado ni patrocinado por Daewoo, IDO (idoosmart) ni Bethesda.
> "Daewoo", "VeryFit" y "Pip-Boy" son marcas de sus respectivos dueños y se mencionan solo para identificar
> el reloj compatible y el estilo de la esfera. Este repositorio **no incluye** código, librerías ni claves de
> IDO/VeryFit: cada usuario los extrae de su propia instalación (ver abajo). Úsalo bajo tu propia responsabilidad.

## Archivos que NO están en el repo (propietarios)

El SDK de IDO (`app/libs/*.jar`) y las librerías nativas (`app/src/main/jniLibs/`) no se distribuyen:
cada quien los extrae de **su propia** instalación de VeryFit 3.4.0 (`com.sport.fitplus1`) con:

```sh
tools/extract_sdk.sh                                        # teléfono conectado por adb con VeryFit
tools/extract_sdk.sh base.apk split_config.arm64_v8a.apk     # o desde los APK
./gradlew assembleDebug
```

Requiere `dex2jar` (`brew install dex2jar`), un JDK 17+ y, en modo teléfono, `adb`.

## Pruebas por adb (sin tocar el teléfono)

```sh
tools/cmd.sh sync|live|info|functions|find|time|connect
tools/cmd.sh notify "texto"
tools/tap.sh X Y     # toca solo si la app está en primer plano
```

Solo una app puede estar vinculada al reloj: con VeryFit activa se pelean la conexión.

## Esferas propias

`tools/faces/pipboy/build.py` genera la esfera **Pip-Boy** (terminal verde fósforo) en el formato
de esferas IDO del DW26 (pantalla 240×284, `iwf.json` + PNG por glifo) y la deja en
`app/src/main/assets/faces/pipboy.zip`, que la app instala desde la pestaña Esferas → Exclusivas.

```sh
python3 -I tools/faces/pipboy/build.py
```

Tipografía: Share Tech Mono (SIL OFL 1.1, ver `tools/faces/OFL.txt`). Esfera de fan, inspirada en el Pip-Boy de Fallout.
El reloj guarda las esferas descomprimidas (~300 KB cada una) y tiene poco espacio:
para agregar una normalmente hay que quitar otra.

## Licencia

Código propio bajo licencia MIT (ver `LICENSE`). La tipografía de `tools/faces/` tiene su propia licencia (OFL).
El SDK y las librerías de IDO que extrae `tools/extract_sdk.sh` **no** están cubiertos por esta licencia.
