#!/bin/bash
# Extrae del VeryFit instalado en TU teléfono (o de sus APK) lo que la app necesita y no está en el repo:
#   app/libs/idoble.jar        SDK de IDO (com.ido.ble, com.veryfit + paquetes ofuscados que usa)
#   app/libs/ido-deps.jar      dependencias del SDK en las mismas versiones (gson, greendao, nordic, …)
#   app/src/main/jniLibs/arm64-v8a/*.so   librería nativa del protocolo y auxiliares
#   ido.properties             clave pública de la app VeryFit para el catálogo de esferas (servidores de IDO)
#
# Uso:
#   tools/extract_sdk.sh                      # desde el teléfono conectado por adb (VeryFit instalada)
#   tools/extract_sdk.sh base.apk split_config.arm64_v8a.apk   # desde APK ya descargados
#
# Requiere: adb (solo modo teléfono), d2j-dex2jar (brew install dex2jar), JDK (jar, jdeps), unzip.
# Probado con VeryFit 3.4.0 (com.sport.fitplus1); otras versiones cambian los nombres ofuscados,
# por eso los paquetes se calculan con jdeps en vez de estar fijos.
set -euo pipefail

PKG=com.sport.fitplus1
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

need() { command -v "$1" >/dev/null || { echo "Falta '$1'. $2" >&2; exit 1; }; }
need d2j-dex2jar "Instálalo con: brew install dex2jar"
need jar "Instala un JDK 17+"
need jdeps "Instala un JDK 17+"
need unzip ""

# ---------- 1. conseguir los APK ----------
if [ $# -ge 2 ]; then
  cp "$1" "$WORK/base.apk"; cp "$2" "$WORK/arm64.apk"
else
  ADB="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
  [ -x "$ADB" ] || { echo "No encuentro adb. Pasa los APK como argumentos o define ADB=/ruta/adb" >&2; exit 1; }
  paths="$("$ADB" shell pm path "$PKG" | tr -d '\r' | sed 's/^package://')"
  [ -n "$paths" ] || { echo "VeryFit ($PKG) no está instalada en el teléfono conectado" >&2; exit 1; }
  # "|| true": grep -m1 corta la salida de dumpsys y con pipefail eso cuenta como error
  ver="$("$ADB" shell dumpsys package "$PKG" | tr -d '\r' | grep -m1 versionName | sed 's/.*=//' || true)"
  echo "VeryFit $ver encontrada en el teléfono"
  [ "$ver" = "3.4.0" ] || echo "Aviso: probado con 3.4.0; con $ver puede necesitar ajustes" >&2
  "$ADB" pull "$(grep '/base.apk$' <<<"$paths")" "$WORK/base.apk" >/dev/null
  "$ADB" pull "$(grep 'split_config.arm64_v8a.apk$' <<<"$paths")" "$WORK/arm64.apk" >/dev/null \
    || { echo "No hay split arm64 (¿teléfono de 32 bits?)" >&2; exit 1; }
fi

# ---------- 2. dex → jar ----------
mkdir -p "$WORK/jars" "$WORK/sdk" "$WORK/deps"
unzip -qo "$WORK/base.apk" 'classes*.dex' -d "$WORK/dex"
for d in "$WORK"/dex/classes*.dex; do
  d2j-dex2jar -f -o "$WORK/jars/$(basename "$d" .dex).jar" "$d" >/dev/null 2>&1
done
take() { # take <destino> <prefijo>… : copia esos paquetes desde todos los jars
  local dest="$1"; shift
  for j in "$WORK"/jars/*.jar; do
    for p in "$@"; do unzip -qo "$j" "$p/*" -d "$dest" 2>/dev/null || true; done
  done
}

# ---------- 3. SDK + paquetes ofuscados de nivel raíz que usa (clausura con jdeps) ----------
take "$WORK/sdk" com/ido/ble com/veryfit
for _ in $(seq 1 10); do
  (cd "$WORK/sdk" && jar cf "$WORK/idoble.jar" .)
  # paquetes de una sola palabra = nombres ofuscados de R8 (acorn, ember, …); "not" es ruido de jdeps
  missing=$(jdeps -verbose:package --ignore-missing-deps "$WORK/idoble.jar" 2>/dev/null \
    | awk '/not found/ {print $3}' | sort -u | grep -E '^[a-z]+$' | grep -vxE 'not|kotlin' || true)
  new=""; for p in $missing; do [ -d "$WORK/sdk/$p" ] || new="$new $p"; done
  [ -z "$new" ] && break
  take "$WORK/sdk" $new
done

# ---------- 4. dependencias (mismas versiones que en VeryFit) ----------
# las de DFU/firmware no se usan, pero el init del SDK las referencia
take "$WORK/deps" com/google/gson com/alibaba/fastjson org/greenrobot/greendao no/nordicsemi/android \
  okhttp3 okio com/realsil com/sifli io/runtime/mcumgr com/clj cn/hutool/core
# paquetes ofuscados que solo usan las dependencias y no quedaron en el SDK
for _ in $(seq 1 5); do
  (cd "$WORK/deps" && jar cf "$WORK/ido-deps.jar" .)
  missing=$(jdeps -verbose:package --ignore-missing-deps -cp "$WORK/ido-deps.jar" "$WORK/idoble.jar" "$WORK/ido-deps.jar" 2>/dev/null \
    | awk '/not found/ {print $3}' | sort -u | grep -E '^[a-z]+$' | grep -vxE 'not|kotlin' || true)
  new=""; for p in $missing; do [ -d "$WORK/sdk/$p" ] || [ -d "$WORK/deps/$p" ] || new="$new $p"; done
  [ -z "$new" ] && break
  take "$WORK/deps" $new
done

# ---------- 5. librerías nativas ----------
LIBS="libVeryFitMulti.so libRtkAesJni.so libezip.so libsfwatchface.so"
for l in $LIBS; do unzip -qo "$WORK/arm64.apk" "lib/arm64-v8a/$l" -d "$WORK/so"; done

# ---------- 6. clave de VeryFit para el catálogo de esferas ----------
APP_KEY=""
for j in "$WORK"/jars/*.jar; do
  if unzip -l "$j" 'com/ido/life/constants/Constants.class' >/dev/null 2>&1; then
    APP_KEY=$(javap -cp "$j" -constants com.ido.life.constants.Constants | sed -n 's/.* APP_KEY = "\(.*\)";/\1/p')
    break
  fi
done
[ -n "$APP_KEY" ] || echo "Aviso: no encontré APP_KEY; el catálogo de esferas no funcionará" >&2

# ---------- 7. copiar al proyecto ----------
mkdir -p "$ROOT/app/libs" "$ROOT/app/src/main/jniLibs/arm64-v8a"
cp "$WORK/idoble.jar" "$WORK/ido-deps.jar" "$ROOT/app/libs/"
cp "$WORK"/so/lib/arm64-v8a/*.so "$ROOT/app/src/main/jniLibs/arm64-v8a/"
printf 'ido.appKey=%s\n' "$APP_KEY" > "$ROOT/ido.properties"

echo "Listo:"
ls -la "$ROOT/app/libs" "$ROOT/app/src/main/jniLibs/arm64-v8a" | grep -E '\.(jar|so)$'
echo "ido.properties: ${APP_KEY:+clave encontrada}"
echo "Compila con: ./gradlew assembleDebug"
