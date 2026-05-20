# 🎵 SonicLens — Multimedia Android App

> Una aplicación multimedia con identidad propia: estética cyberpunk, un theremin con el acelerómetro, cámara con efectos glitch, conversión de imágenes con pixel art, y reproductor de vídeo.

---

## ⚡ Setup rápido en Android Studio

### Paso 1 — Crear proyecto base

| Campo | Valor |
|---|---|
| Template | **Empty Views Activity** |
| Name | SonicLens |
| Package | `com.example.soniclens` |
| Language | **Kotlin** |
| Min SDK | API 26 (Android 8.0) |
| Build config | **Kotlin DSL** (`.kts`) |

> ⚠️ Importante: en "Build configuration language" elige **Kotlin DSL**, no Groovy.

### Paso 2 — Reemplazar archivos

Copia todos los archivos del ZIP manteniendo la misma estructura de carpetas.  
Los archivos críticos que **debes reemplazar** (no añadir junto a los originales):

```
/                               ← raíz del proyecto
├── settings.gradle.kts         ← reemplaza settings.gradle.kts
├── build.gradle.kts            ← reemplaza build.gradle.kts
├── gradle.properties           ← reemplaza
├── gradle/
│   ├── libs.versions.toml      ← NUEVO (version catalog)
│   └── wrapper/
│       └── gradle-wrapper.properties  ← reemplaza
└── app/
    ├── build.gradle.kts        ← reemplaza
    ├── proguard-rules.pro      ← reemplaza
    └── src/main/
        ├── AndroidManifest.xml ← reemplaza
        ├── java/...            ← copia todos los .kt
        └── res/...             ← copia todos los recursos
```

### Paso 3 — Sync & Build

```
File → Sync Project with Gradle Files
Build → Make Project
```

### Paso 4 — Ejecutar

Conecta un **dispositivo físico Android** (recomendado: API 26+).  
Run → Run 'app'

---

## 📱 Los 4 módulos

### 01 · THEREMIN ESPACIAL 🎵
El módulo de grabación de audio más original posible.

- **Acelerómetro → música**: inclina el móvil izquierda/derecha para cambiar el pitch (110–880 Hz, escala cromática). Inclina adelante/atrás para cambiar el volumen.
- **Síntesis en tiempo real** con `AudioTrack` y PCM: onda sinusoidal + armónicos (fundamental + 2ª + 3ª) para un sonido tipo theremin auténtico.
- **Grabación paralela** con `MediaRecorder`: mientras el theremin suena, graba el micrófono (capturas lo que el altavoz emite + ambiente).
- **Reproducción** del archivo `.m4a` grabado.
- Muestra la nota musical, frecuencia en Hz, octava, y barras de pitch/timbre en tiempo real.

### 02 · GLITCHCAM 📸
La cámara con alma de artista glitch.

- Preview en vivo con **CameraX**.
- Captura la foto en memoria como `ByteArray`.
- Aplica uno de 5 **efectos visuales** implementados a mano píxel a píxel:
  - **NORMAL**: sin efecto
  - **GLITCH**: desplaza filas aleatorias horizontalmente + corrompe píxeles
  - **SCANLINES**: líneas oscuras cada 2px + tinte cian
  - **ABERRACIÓN CROMÁTICA**: separa canales R, G, B ~1.5% del ancho
  - **INVERTIR**: invierte todos los colores
- Guarda en Galería (`Pictures/SonicLens/`) en JPEG con el nombre del efecto.
- Puedes cambiar el efecto y re-aplicarlo a la misma foto sin volver a capturar.

### 03 · PIXELFORGE 🖼️
Procesamiento de imagen con alma retro.

- Carga cualquier imagen con `ActivityResultContracts.GetContent()`.
- **Conversión PNG → JPG** y **JPG → PNG** con `Bitmap.compress()`.
- Botón **PIXELAR**: efecto pixel art auténtico — reduce la imagen a 1/40 de su tamaño (sin filtro = pixelado duro) y la re-escala al tamaño original, generando el clásico look de píxeles visibles de 8 bits.
- Muestra formato, resolución y tamaño del resultado antes de guardar.
- Guarda en Galería.

### 04 · CINEMAVAULT 🎬
Reproductor de vídeo profesional.

- Carga vídeos con selector del sistema.
- `VideoView` con controles nativos.
- SeekBar sincronizada con el progreso (actualización cada 250ms).
- Muestra tiempo actual / duración total.
- Extrae metadatos: resolución, título, con `MediaMetadataRetriever`.
- Controles ▶ / ⏸ / ⏹ con indicadores visuales en el título.

---

## 🛠️ Stack técnico

| Componente | Tecnología |
|---|---|
| Lenguaje | Kotlin 1.9.23 |
| JVM target | Java 17 (compatible JDK 21) |
| Min SDK | API 26 (Android 8.0) |
| Target SDK | API 34 (Android 14) |
| Gradle | 8.6 + Version Catalog |
| AGP | 8.3.2 |
| Cámara | CameraX 1.3.3 |
| UI | Material Components 1.12 + ViewBinding |
| Audio síntesis | AudioTrack PCM 44100Hz |
| Audio grabación | MediaRecorder M4A/AAC |
| Vídeo | VideoView + MediaMetadataRetriever |
| Imágenes | Bitmap API + Canvas |
| Sensores | SensorManager + TYPE_ACCELEROMETER |

---

## 🔑 Permisos

| Permiso | Módulo |
|---|---|
| `RECORD_AUDIO` | Theremin (grabación) |
| `CAMERA` | GlitchCam |
| `READ_MEDIA_IMAGES` (API 33+) | PixelForge |
| `READ_MEDIA_VIDEO` (API 33+) | CinemaVault |
| `READ_EXTERNAL_STORAGE` (API ≤ 32) | PixelForge / CinemaVault |

---

## ⚠️ Notas

- **JDK 21 (Temurin)** + **Java 17 target** → compatibilidad perfecta (JDK 21 soporta bytecode hasta Java 21, pero el target 17 asegura compatibilidad con todas las versiones Android modernas).
- El theremin graba desde el **micrófono**, no el AudioTrack directamente — esto es una limitación de Android (no se puede capturar el playback interno sin root). El resultado es el ambiente + el sonido del altavoz.
- Para el efecto Glitch, cada captura genera resultados distintos (semilla `System.currentTimeMillis()`).
