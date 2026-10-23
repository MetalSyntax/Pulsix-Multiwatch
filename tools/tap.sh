#!/bin/bash
# Toca la pantalla solo si DW26 está en primer plano (para no tocar otra app del usuario)
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB shell dumpsys activity activities | grep -q 'topResumedActivity.*com.metalsyntax.dw26' || { echo "DW26 no está en primer plano"; exit 1; }
$ADB shell input tap "$1" "$2"
