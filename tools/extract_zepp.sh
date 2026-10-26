#!/bin/bash
# Extrae de Zepp Active (com.huami.watch.pop, la app del Amazfit Pop 2) el SDK de FitCloud/htsmart:
#   app/libs/htsmart.jar     com.htsmart.wristband2 + RxJava2/RxAndroid/RxAndroidBle/RxRelay + Realtek DFU (mismas versiones)
#   app/src/main/jniLibs/arm64-v8a/libRtkAesJni.so   versión de Zepp (la Java de Realtek que va en htsmart.jar la necesita)
# y quita com/realsil de app/libs/ido-deps.jar: VeryFit trae otra versión de Realtek y chocarían (clases duplicadas).
# Correr DESPUÉS de tools/extract_sdk.sh.
#
# Uso:
#   tools/extract_zepp.sh                                       # desde el teléfono conectado por adb
#   tools/extract_zepp.sh base.apk split_config.arm64_v8a.apk   # desde APK ya descargados
# Probado con Zepp Active 1.5.1.28.
set -euo pipefail

PKG=com.huami.watch.pop
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

need() { command -v "$1" >/dev/null || { echo "Falta '$1'. $2" >&2; exit 1; }; }
need d2j-dex2jar "Instálalo con: brew install dex2jar"
need jar "Instala un JDK 17+"
need zip ""
need jdeps "Instala un JDK 17+"
[ -f "$ROOT/app/libs/ido-deps.jar" ] || { echo "Primero corre tools/extract_sdk.sh (VeryFit)" >&2; exit 1; }

if [ $# -ge 2 ]; then
  cp "$1" "$WORK/base.apk"; cp "$2" "$WORK/arm64.apk"
else
  ADB="${ADB:-$(command -v adb || echo "$HOME/Library/Android/sdk/platform-tools/adb")}"
  paths="$("$ADB" shell pm path "$PKG" | tr -d '\r' | sed 's/^package://')"
  [ -n "$paths" ] || { echo "Zepp Active ($PKG) no está instalada en el teléfono conectado" >&2; exit 1; }
  "$ADB" pull "$(grep '/base.apk$' <<<"$paths")" "$WORK/base.apk" >/dev/null
  "$ADB" pull "$(grep 'split_config.arm64_v8a.apk$' <<<"$paths")" "$WORK/arm64.apk" >/dev/null
fi

mkdir -p "$WORK/jars" "$WORK/sdk"
unzip -qo "$WORK/base.apk" 'classes*.dex' -d "$WORK/dex"
for d in "$WORK"/dex/classes*.dex; do
  d2j-dex2jar -f -o "$WORK/jars/$(basename "$d" .dex).jar" "$d" >/dev/null 2>&1
done
for j in "$WORK"/jars/*.jar; do
  for p in com/htsmart com/polidea bleshadow io/reactivex com/jakewharton/rxrelay2 org/reactivestreams com/realsil; do
    unzip -qo "$j" "$p/*" -d "$WORK/sdk" 2>/dev/null || true
  done
done
[ -f "$WORK/sdk/com/htsmart/wristband2/WristbandManager.class" ] || { echo "No encontré el SDK htsmart en el APK" >&2; exit 1; }
(cd "$WORK/sdk" && jar cf "$WORK/htsmart.jar" .)
# R8 de Zepp movió backports sintéticos de RxJava a clases ajenas (androidx.camera…$$ExternalSyntheticBackport…):
# copiarlos y renombrarlos, porque D8 rechaza "$$ExternalSynthetic" fuera de su contexto
n=0
for c in $(jdeps -verbose:class --ignore-missing-deps "$WORK/htsmart.jar" 2>/dev/null | awk '/not found/ {print $3}' | grep -F '$$ExternalSynthetic' | sort -u); do
  f="$(tr . / <<<"$c")"
  for j in "$WORK"/jars/*.jar; do unzip -qo "$j" "$f.class" -d "$WORK/sdk" 2>/dev/null && break; done
  python3 "$ROOT/tools/rename_class.py" "$WORK/sdk" "$f" "com/htsmart/backport/Backport$n"
  n=$((n + 1))
done
(cd "$WORK/sdk" && jar cf "$ROOT/app/libs/htsmart.jar" .)

zip -qd "$ROOT/app/libs/ido-deps.jar" 'com/realsil/*' 2>/dev/null || true
unzip -qo "$WORK/arm64.apk" lib/arm64-v8a/libRtkAesJni.so -d "$WORK/so"
cp "$WORK/so/lib/arm64-v8a/libRtkAesJni.so" "$ROOT/app/src/main/jniLibs/arm64-v8a/"
echo "Listo: app/libs/htsmart.jar ($(du -h "$ROOT/app/libs/htsmart.jar" | cut -f1)), libRtkAesJni.so de Zepp, ido-deps.jar sin com/realsil"
