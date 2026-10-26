# Esferas Dinámicas y Personalización Pip-Boy (DW26 / Pulsix)

Este documento detalla la viabilidad técnica de crear esferas dinámicas (fondos animados) en el **Daewoo DW26** y la guía para personalizar la esfera **Pip-Boy** con la figura clásica de **Vault-Boy** de manera privada sin comprometer el repositorio de GitHub.

---

## 1. Viabilidad de Esferas Dinámicas en el DW26

### ¿Se puede hacer que el fondo se mueva o esté animado?
**No de manera continua o mediante vídeo/GIF en este modelo.**

El reloj cuenta con limitaciones fundamentales a nivel de arquitectura:

1. **Formato IWF (IDO Watch Face)**:
   - El firmware del DW26 (basado en la plataforma RTOS de IDO, dial versión 6) utiliza una especificación donde el fondo está definido como un bitmap estático único:
     ```json
     "bkground": "background.png"
     ```
   - El motor de renderizado embebido no implementa decodificación de GIFs animados ni reproducción cíclica de secuencias de vídeo para el tapiz de fondo.
2. **Límite crítico de almacenamiento Flash**:
   - El DW26 dispone de aproximadamente **~140 KB libres** de almacenamiento interno cuando mantiene las esferas de fábrica.
   - Una pantalla de resolución $240 \times 284$ a color de 16 bits (RGB565) ocupa $240 \times 284 \times 2 = 136.320\text{ bytes}$ (~$133\text{ KB}$) por cada fotograma sin comprimir.
   - Una animación de tan solo 5 fotogramas saturaría por completo la memoria flash del reloj, provocando el error `código 21: sin espacio` durante la transferencia BLE.

### Elementos dinámicos que sí están soportados
En lugar de un fondo en movimiento, el dinamismo se logra a través de los widgets que refresca el procesador del reloj:
- **Segundero continuo**: El widget `second` se refresca segundo a segundo.
- **Telemetría de salud y sensores**: Frecuencia cardíaca (BPM), nivel de batería (%), contador de pasos, calorías quemadas y temperatura meteorológica.

---

## 2. Personalización de Pip-Boy con Vault-Boy (100% Local / Privada)

La esfera Pip-Boy incluida por defecto en Pulsix (`tools/faces/pipboy/build.py`) utiliza un **trébol de radiación** generado por código matemático para evitar infracciones de copyright (DMCA) frente a Bethesda/ZeniMax en GitHub.

Para tener el diseño con **Vault-Boy** en tu propio reloj sin que se suba a GitHub:

### Paso 1: Ubicación del archivo de imagen
Coloca la silueta o ilustración de Vault-Boy (preferiblemente PNG monocromático o con fondo transparente) dentro del directorio `private/`:

```text
dw26_watch/private/vaultboy.png
```

> [!NOTE]
> La carpeta `private/` está registrada en `.gitignore`, por lo que el archivo binario nunca será rastreado ni subido a GitHub.

---

### Paso 2: Adaptar el generador de la esfera (`tools/faces/pipboy/build.py`)

Abre `tools/faces/pipboy/build.py` y localiza en la función `background()` la sección donde se dibuja el trébol de radiación (aproximadamente líneas 83 a 90):

```python
    # icono de radiación (trébol) a la izquierda
    cx, cy, r = 44, 196, 30
    d.ellipse([cx - r - 6, cy - r - 6, cx + r + 6, cy + r + 6], outline=DIM, width=2)
    for a in (90, 210, 330):
        d.pieslice([cx - r, cy - r, cx + r, cy + r], a - 30, a + 30, fill=GREEN)
    d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=BG)
    d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=GREEN)
    im.alpha_composite(im.filter(ImageFilter.GaussianBlur(1.5)).point(lambda v: v // 3))
```

Reemplaza ese bloque por el cargador automático de la silueta:

```python
    # Cargar ilustración personalizada de Vault-Boy (si existe en private/)
    vb_file = os.path.join(ROOT, "private", "vaultboy.png")
    if os.path.exists(vb_file):
        vb_raw = Image.open(vb_file).convert("RGBA")
        # El área disponible a la izquierda de los stats es de ~72x80 px
        vb_raw.thumbnail((72, 80), Image.LANCZOS)

        # Aplicar el tinte verde fósforo idéntico al resto de la terminal
        alpha = vb_raw.split()[3] if len(vb_raw.split()) == 4 else vb_raw.convert("L")
        vb_green = Image.new("RGBA", vb_raw.size, (0, 0, 0, 0))
        vb_green.paste(Image.new("RGBA", vb_raw.size, GREEN + (255,)), (0, 0), alpha)

        # Centrar en la zona de estado (x=8, y=154)
        im.alpha_composite(vb_green, (8, 154))
    else:
        # Fallback: Trébol de radiación estándar
        cx, cy, r = 44, 196, 30
        d.ellipse([cx - r - 6, cy - r - 6, cx + r + 6, cy + r + 6], outline=DIM, width=2)
        for a in (90, 210, 330):
            d.pieslice([cx - r, cy - r, cx + r, cy + r], a - 30, a + 30, fill=GREEN)
        d.ellipse([cx - 9, cy - 9, cx + 9, cy + 9], fill=BG)
        d.ellipse([cx - 6, cy - 6, cx + 6, cy + 6], fill=GREEN)
```

---

### Paso 3: Compilar la esfera

Ejecuta el script de generación:

```bash
python3 -I tools/faces/pipboy/build.py
```

Esto actualizará los binarios locales:
- `app/src/main/assets/faces/pipboy.zip`
- `app/src/main/assets/faces/pipboy.png`

---

### Paso 4: Transferir al reloj

1. Abre la aplicación **Pulsix** en tu teléfono Android.
2. Ve a la pestaña **Faces**.
3. Selecciona la esfera **Pip-Boy**.
4. Pulsa **Instalar** (si el reloj está lleno, la app te solicitará cuál esfera preexistente deseas reemplazar).

---

### Paso 5: Evitar enviar modificaciones de código a GitHub

Para evitar que `git status` o `git push` detecten los cambios en el archivo `build.py`, dile a Git que ignore los cambios locales en ese script:

```bash
git update-index --assume-unchanged tools/faces/pipboy/build.py
```

Si en el futuro deseas volver a rastrear los cambios oficiales del script:
```bash
git update-index --no-assume-unchanged tools/faces/pipboy/build.py
```
