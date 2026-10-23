# Daewoo DW26 — app propia

App Android (Kotlin) para el smartwatch Daewoo DW26 (firmware 1.01.00, plataforma IDO).
Usa el SDK de IDO que trae VeryFit (`com.ido.ble`) y su librería nativa de protocolo.

## Archivos que NO están en el repo (propietarios)

Hay que sacarlos de VeryFit 3.4.0 (`com.sport.fitplus1`) instalada en un Android arm64:

| Destino | Origen |
|---|---|
| `app/libs/idoble.jar` | clases `com/ido/ble`, `com/veryfit` y paquetes ofuscados de nivel raíz que usan (dex2jar sobre `base.apk`) |
| `app/libs/ido-deps.jar` | gson, fastjson, greendao, nordic ble/dfu, okhttp, okio, realsil, sifli, mcumgr, clj, hutool-core del mismo APK |
| `app/src/main/jniLibs/arm64-v8a/` | `libVeryFitMulti.so`, `libRtkAesJni.so`, `libezip.so`, `libsfwatchface.so` de `split_config.arm64_v8a.apk` |

```sh
adb shell pm path com.sport.fitplus1   # rutas de base.apk y split_config.arm64_v8a.apk
```

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

Tipografía: Share Tech Mono (SIL OFL 1.1, ver `tools/faces/OFL.txt`).
El reloj guarda las esferas descomprimidas (~300 KB cada una) y tiene poco espacio:
para agregar una normalmente hay que quitar otra.
