# TagEdit

Esqueleto inicial de un editor de tags de audio para Android (estilo mp3tag), pensado
para compilarse vía GitHub Actions.

## Qué incluye
- Proyecto Gradle (Kotlin DSL) mínimo, sin gradle wrapper committeado: el workflow de
  CI usa `gradle/actions/setup-gradle` para instalar Gradle, así que no hace falta
  generar el `gradlew` a mano. Si preferís tenerlo para compilar local, corré
  `gradle wrapper` una vez tengas Gradle instalado y committeá los archivos generados.
- `net.jthink:jaudiotagger:3.0.1` como librería de lectura/escritura de tags
  (MP3, FLAC, OGG, M4A, WAV).
- `MainActivity` con selector de carpeta vía Storage Access Framework (SAF) y listado
  básico de archivos de audio.

## Cómo compilar
1. Subí este proyecto a un repo de GitHub.
2. El workflow en `.github/workflows/build.yml` corre en cada push a `main` y sube el
   APK debug como artifact descargable desde la pestaña Actions.

## Punto importante: SAF + jaudiotagger
jaudiotagger trabaja con `java.io.File`, no con `Uri` de SAF directamente. Como en
Android 10+ no podés tener un path real de las carpetas fuera de tu sandbox, el flujo
típico es:
1. Copiar el archivo elegido a un directorio temporal interno (`cacheDir`) usando el
   `Uri` de SAF.
2. Editar el tag ahí con jaudiotagger.
3. Volcar el archivo modificado de vuelta a la ubicación original vía
   `DocumentFile`/`OutputStream`.

Esto es más lento que trabajar con paths directos, pero es la única forma confiable
dentro del scoped storage. Si el rendimiento en carpetas grandes es un problema, se
puede evaluar más adelante migrar a TagLib nativo con JNI (igual que se usa
FFmpegKit en METROX).

## Próximos pasos sugeridos
- RecyclerView con selección múltiple para la lista de canciones.
- Motor de patrones Tag↔Filename (parser de placeholders tipo `%artist% - %title%`).
- Diálogo de edición masiva de tags.
- Numeración automática de pistas.
