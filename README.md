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
Android 10+ no podés tener un path real de las carpetas fuera de tu sandbox, `TagIO`
resuelve esto así:
1. Copia el archivo elegido a un directorio temporal interno (`cacheDir`) usando el
   `Uri` de SAF.
2. Lee/edita el tag ahí con jaudiotagger.
3. Si escribió algo, vuelca el archivo modificado de vuelta a la ubicación original vía
   `ContentResolver.openOutputStream(uri, "wt")`.

Esto es más lento que trabajar con paths directos, pero es la única forma confiable
dentro del scoped storage. Si el rendimiento en carpetas grandes es un problema, se
puede evaluar más adelante migrar a TagLib nativo con JNI (igual que se usa
FFmpegKit en METROX).

## Troubleshooting
- **jaudiotagger y `java.util.logging` en Android**: es un problema conocido de esta
  librería en Android — en algunos dispositivos/versiones la inicialización de sus
  loggers estáticos puede tirar `NoSuchMethodError` o similar porque el
  `java.util.logging` de Android es un subset del de la JVM. Si al correr `AudioFileIO.read()`
  o `.commit()` ves un crash relacionado a `LogManager` o `Logger`, la salida más rápida
  es silenciar esos loggers al arrancar la app (`Logger.getLogger("org.jaudiotagger").level = Level.OFF`,
  recursivamente en los loggers hijos) o, si persiste, migrar a TagLib nativo vía JNI.
  Como no hay forma de compilar/correr esto en este entorno para confirmarlo, conviene
  probarlo temprano en un dispositivo/emulador real antes de construir mucho más encima.

## Funcionalidad actual
- Elegir carpeta (SAF) y listar archivos de audio.
- Selección múltiple (tap / long-press) + "Todo" / "Ninguno".
- Diálogo de edición masiva:
  - **Editar tags**: escribe los campos no vacíos en todos los seleccionados (jaudiotagger).
  - **Tag → Nombre**: lee tags reales, genera el nombre con el patrón y renombra el archivo.
  - **Nombre → Tag**: parsea el nombre con el patrón y escribe los valores extraídos como tags.
  - **Numeración automática**: asigna `track` secuencial (con padding) a los seleccionados, en el orden de la lista.

## Próximos pasos sugeridos
- Progreso visual (barra o spinner) durante las operaciones en bloque, hoy solo hay un Toast al final.
- Manejo más fino de errores por archivo (hoy solo se cuenta éxito/fracaso total).
- Soporte de más formatos si hace falta (WMA, APE) o migración a TagLib si jaudiotagger da problemas en Android.
