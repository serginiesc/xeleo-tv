# XELEO TV para onn TV

Pantalla de inicio para el **onn 4K Streaming Box** (Google TV), sin anuncios ni recomendaciones: todo lo que
aparece es lo que tú instalaste. Hecha y probada en un onn 4K con Google TV 14; debería funcionar en otros
aparatos con Google TV o Android TV, pero no se ha probado en ellos.

## Qué trae

- **Paisaje que sigue la hora:** amanecer, día, atardecer y noche, calculados con la salida y puesta del sol de tu
  ciudad. Si llueve, nieva, hay tormenta o neblina, el paisaje lo muestra (con lluvia y relámpagos animados).
- **Cielo vivo:** de noche las estrellas titilan y se desplazan despacio, con alguna estrella fugaz o un cometa de
  vez en cuando; de día pasan nubes y parvadas de pájaros. Se pausa en cuanto abres una app.
- **Clima** de tu ciudad en la línea de abajo (Open-Meteo, sin clave).
- **Saludo y nombre de la tele** ("Buenas noches, Ana" · "Ana TV"), editables desde la tele.
- **Recientes:** botones con las últimas apps que abriste, vengan del inicio, del control o de Gemini.
- **Fila de apps** con tus favoritas primero. Deja presionado OK sobre una app para moverla con ◀ ▶.
- **Reproductor:** lo que suena en Spotify, YouTube, etc., con anterior / pausa / siguiente, en el inicio y en el
  menú rápido.
- **Menú rápido** al estilo Google TV (botón Ajustes o dejar presionado Inicio): protector de pantalla, Wi‑Fi,
  imagen, Bluetooth, sonido, accesibilidad, **temporizador para dormir** (15/30/60/90 min), apps, Personalizar y
  **Reiniciar**.
- **Protector de pantalla** a juego: paisaje, reloj grande que se mueve poco a poco, clima y la canción.

## Instalar

En la tele: Ajustes → Sistema → Acerca de → presiona 7 veces *Compilación del SO*; luego Opciones de desarrollador →
**Depuración USB** activada. En la computadora (Git Bash en Windows), con JDK 17 y el SDK de Android:

```bash
./install.sh 192.168.1.50
```

Usa la IP de tu onn (Ajustes → Red e Internet → tu red). La tele pregunta "¿Permitir depuración USB?": acepta.
El script compila, instala y deja XELEO TV como inicio, protector de pantalla y servicio del control. Al
terminar, apaga otra vez la Depuración USB.

En Google TV conviene desactivar también el inicio de Google, o se queda con el botón Inicio:

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

(`pm enable` con los mismos nombres los regresa.) Antes de desactivar `setupwraith`, revisa que tu red Wi‑Fi no
sea suya (`adb shell dumpsys wifi | grep -o 'cname=[^ ]*'`): si lo es, olvida la red y vuelve a conectarte.

## Cambiar cosas

- **Nombre, saludo y ciudad del clima:** en la tele, menú rápido → Personalizar. De fábrica no hay nombre
  ("Mi TV") y el clima es de Ciudad de México.
- **Apps favoritas por defecto:** `dock_apps` en `app/src/main/res/values/config.xml`.
- **Probar otro clima en el paisaje** (para ver cómo se ve):
  `adb shell am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.WEATHER_PREVIEW --ei code 61`
  (61 lluvia, 95 tormenta, 45 neblina, 71 nieve, 2 nubes, -1 el clima real).

## Notas

- Java puro, sin librerías. `minSdk 26`, `targetSdk 34`.
- La app necesita acceso a notificaciones (solo para saber qué se está reproduciendo) y un servicio de
  accesibilidad (botón Inicio largo, temporizador, reinicio y recientes); `install.sh` los activa.
- Fuente: [Outfit](https://github.com/Outfitio/Outfit-Fonts), licencia SIL OFL 1.1 (`FONT-LICENSE-OFL.txt`).
  Íconos: Material Icons de Google (Apache 2.0).
