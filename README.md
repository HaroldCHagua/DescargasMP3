# Descargas MP3 Limpia

Proyecto Android independiente, separado del proyecto anterior. No usa Termux ni un servidor propio.

## Compilar en Ubuntu
Necesita Android SDK Platform 35, JDK 17 o 21, `curl` y `unzip`.

```bash
chmod +x build_apk.sh
./build_apk.sh
```

APK: `app/build/outputs/apk/debug/app-debug.apk`

La aplicación guarda el MP3 en `Música/DescargasMP3`. El motor de descarga y la conversión requieren Internet. La disponibilidad de fuentes de vídeo puede cambiar; no se garantiza que todos los enlaces funcionen. Descarga únicamente contenido propio o autorizado.
