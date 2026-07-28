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
- **Rename no soportado en MIUI/Xiaomi**: `DocumentFile.renameTo()` puede tirar
  `UnsupportedOperationException` en el proveedor SAF de MIUI, que no implementa rename
  directo. `TagIO.renameFile()` ya tiene un fallback automático: si el rename directo
  falla, crea un archivo nuevo con el nombre deseado, copia el contenido, y borra el
  original. Requiere pasarle la carpeta padre (`parentDir`) — sin eso no hay forma de
  crear el archivo nuevo en el lugar correcto.
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
- Tema oscuro forzado (fondo gris `#1C1C1C`, botones gris `#3A3A3A`, texto blanco),
  independiente del modo claro/oscuro del sistema.
- Elegir carpeta (SAF) y listar archivos de audio, mostrando Pista / Título / Artista · Álbum
  leídos en background con jaudiotagger (mientras carga muestra "Leyendo tags…" por fila).
- Selección múltiple (tap / long-press) + "Todo" / "Ninguno", los 4 botones principales en una fila.
- Diálogo de edición masiva:
  - Muestra la carátula embebida arriba, **solo si todas las canciones seleccionadas tienen
    exactamente la misma** (si alguna no tiene carátula, o difieren entre sí, no se muestra nada).
  - **Editar tags**: escribe los campos no vacíos en todos los seleccionados (jaudiotagger).
  - **Vista previa del renombrado**: muestra "nombre actual → nombre nuevo" para cada
    seleccionado, sin tocar ningún archivo — para decidir antes de aplicar.
  - **Tag → Nombre**: lee tags reales, genera el nombre con el patrón y renombra el archivo
    (con fallback de copiar+borrar si el proveedor SAF no soporta rename directo, como en MIUI).
  - **Nombre → Tag**: parsea el nombre con el patrón y escribe los valores extraídos como tags.
  - **Numeración automática**: asigna `track` secuencial (con padding) a los seleccionados, en el orden de la lista.
  - Chips de placeholders (Pista/Título/Artista/Álbum/etc.) que insertan el token `%campo%`
    en el patrón en la posición del cursor, sin tener que tipearlo a mano.

## Próximos pasos sugeridos
- La lectura de tags en background es secuencial y copia cada archivo a cacheDir para leerlo,
  así que en carpetas grandes (cientos de archivos) puede tardar bastante en poblar toda la
  lista. Si se vuelve un problema, evaluar limitar la lectura a los ítems visibles primero,
  o cachear resultados entre sesiones.
- Manejo más fino de errores por archivo (hoy solo se cuenta éxito/fracaso total, con el
  primer error mostrado en un diálogo).
- Soporte de más formatos si hace falta (WMA, APE) o migración a TagLib si jaudiotagger da problemas en Android.
