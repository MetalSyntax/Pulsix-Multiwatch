#!/bin/bash
# Envía un comando de prueba a la app: tools/cmd.sh sync|live|info|functions|find|time|notify|connect
ADB=~/Library/Android/sdk/platform-tools/adb
$ADB shell am broadcast -n com.metalsyntax.dw26/.AdbCommands --es cmd "$1" ${2:+--es text "$2"} >/dev/null
