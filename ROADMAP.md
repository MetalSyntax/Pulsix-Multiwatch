# Pendientes — funciones de VeryFit que el DW26 admite

Lista sacada de la tabla de funciones que reporta el reloj (`tools/cmd.sh dumpfunctions` → `files/functions.json`)
cruzada con lo que ya tiene la app (v0.5). Orden sugerido: 1, 4 y 6 primero.

Antes de implementar cada una: revisar en el código de VeryFit (jadx, `com/ido/life`) cómo arma el modelo
y qué callback confirma, igual que se hizo con alarmas, clima y esferas.

## 1. Monitoreo de salud automático
- [ ] Pulso: modo continuo / inteligente / por intervalos — `setHeartRateMeasureModeV3`, `setHeartRateSmart`, `setHeartRateInterval`
- [ ] Alertas de pulso alto y bajo (incluidas en el modo de medición V3) — flag `V3_support_set_smart_heart_rate`
- [ ] SpO2 todo el día y alerta de oxígeno bajo — `setSPO2Param` · flags `V3_support_set_spo2_all_day_on_off`, `V3_support_set_spo2_low_value_remind`
- [ ] Estrés con alerta de nivel alto — `setPressureParam` · flag `ex_table_main11_pressure_high_threshold_reminder`
- [ ] Sueño detallado (fases y puntuación) — `setSleepMonitoringPara` · flag `V3_support_set_scientific_sleep_switch`
- [ ] Mostrar en Salud los picos de pulso alto/bajo que ya sincroniza el reloj (`V3_support_sync_over_high_low_heart_data`)

## 2. Contactos frecuentes
- [ ] Elegir hasta 20 contactos para llamar desde el reloj — `setFrequentContactsV3` · flags `callingContact`, `V3_support_sync_contact` (`contact_max_set_num = 20`)
- [ ] Contactos de emergencia — `setEmergencyContactsV3`

## 3. Recordatorios
- [ ] Sedentarismo (levantarse cada X min, horario y días) — `setLongSit` · flag `sedentariness`
- [ ] Beber agua — `setDrinkWaterReminder` · flags `ex_main4_drink_water_reminder`, `V3_drink_water_add_notify_flag`
- [ ] Lavarse las manos — `setWashHandReminder` · flag `ex_table_main10_set_hand_washing_reminder`

## 4. Pantalla y comodidad
- [ ] No molestar con horario (y todo el día) — `setNotDisturbPara` · flags `doNotDisturb`, `support_v2_disturb_all_day_switch`
- [ ] Levantar la muñeca para encender — `setUpHandGesture` · flag `upHandGestrue`
- [ ] Brillo de pantalla y ajuste nocturno — `setScreenBrightness*` · flags `ex_screen_brightness`, `ex_table_main9_get_screen_brightness`
- [ ] Mano en la que se usa el reloj — `setHandWearMode`

## 5. Cámara remota
- [ ] Disparar la cámara del teléfono desde el reloj — eventos `DeviceControlEventType.TAKE_ONE_PHOTO / OPEN_CAMERA / CLOSE_CAMERA` (ya llegan a `Watch.handle`), `enterCameraMode` / `exitCameraMode` · flags `bleControlTakePhoto`, `support_ble_control_photograph`

## 6. Historial de entrenamientos
- [ ] Sincronizar actividades (caminar, correr, bici, yoga, elíptica, remo, natación…) y mostrarlas en Salud con duración, pulso y calorías — `ISyncDataListener.onGetHealthActivityV3Data` · flags `ex_table_main9_v3_sports`, `V3_sync_v3_activity_add_param`
- [ ] Ordenar los deportes del reloj — `setSport100TypeSort` · flag `V3_set_100_sport_sort`

## 7. Menú del reloj
- [ ] Reordenar u ocultar apps del reloj — `getMenuList` / `setMenuListV3` · flags `V3_get_menu_list`, `ex_main7_menu_list`

## 8. Unidades e idioma
- [ ] km / millas, °C / °F, 12 / 24 h — `setUnit` · flags `unitSet`, `support_fahrenheit`
- [ ] Idioma del reloj (tiene español) — flags `lang_spanish`, `ex_table_main10_v3_get_lang_library`

## 9. Otros
- [ ] Ciclo menstrual — `setMenstrual`, `setMenstrualRemind` · flag `ex_main3_menstruation`
- [ ] Respiración guiada — `setBreatheTrain` · flag `breathe_train`
- [ ] Versión de firmware en la pantalla Reloj — flag `versionInfo`
- [ ] Restablecer de fábrica (con doble confirmación) — `setRestoreFactory` · flag `factory_reset`

## Ya hecho (v0.5)
Conexión y reconexión, salud diaria (pasos, pulso, sueño, estrés, SpO2), notificaciones, llamadas entrantes y perdidas,
música, clima, alarmas, meta de pasos, buscar reloj (llamada falsa), esferas (catálogo + Pip-Boy), temas claro/oscuro/negro.

## Más adelante
- Ruta 2: protocolo propio sin `libVeryFitMulti.so` (captura HCI snoop + reimplementación).
