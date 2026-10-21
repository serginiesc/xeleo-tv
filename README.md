# XELEO TV para onn TV

Pantalla de inicio para el **onn 4K Streaming Box** (Google TV), sin anuncios ni recomendaciones: todo lo que
aparece es lo que tú instalaste. Hecha y probada en un onn 4K con Google TV 14; debería funcionar en otros
aparatos con Google TV o Android TV, pero no se ha probado en ellos.

![Inicio de día con la fila Hoy de partidos y las apps](docs/screenshots/inicio-dia.jpg)

| De noche | Día de partido de tu equipo |
|---|---|
| ![Inicio de noche con el cielo nocturno](docs/screenshots/inicio-noche.jpg) | ![Inicio el día que juega Santos: logo, rival, hora y cuenta regresiva](docs/screenshots/dia-de-partido.jpg) |

| Panel del partido | En vivo |
|---|---|
| ![Panel del partido: marcador por cuarto, líderes, comparativa y tabla de la división](docs/screenshots/panel-partido.jpg) | ![Pestaña En vivo con la guía de Pluto TV y la vista previa del canal](docs/screenshots/en-vivo.jpg) |

| Menú rápido | Todas las apps |
|---|---|
| ![Menú rápido con Deportes y En vivo](docs/screenshots/menu-rapido.jpg) | ![Todas las apps, con palomita en las que están en el inicio](docs/screenshots/todas-las-apps.jpg) |

| Aviso sobre otra app | Protector de pantalla |
|---|---|
| ![Aviso de anotación de los Chiefs encima de otra app](docs/screenshots/aviso.jpg) | ![Protector de pantalla con reloj, clima y canción](docs/screenshots/protector.jpg) |

## Qué trae

### El inicio

- **Paisaje que sigue la hora:** amanecer, día, atardecer y noche, calculados con la salida y puesta del sol de tu
  ciudad. Si llueve, nieva, hay tormenta o neblina, el paisaje lo muestra (con lluvia y relámpagos animados).
- **Cielo vivo:** de noche las estrellas titilan y se desplazan despacio, con alguna estrella fugaz o un cometa de
  vez en cuando; de día pasan nubes y parvadas de pájaros. Se pausa en cuanto abres una app.
- **Clima** de tu ciudad en la línea de abajo (Open-Meteo, sin clave).
- **Saludo y nombre de la tele** ("Buenas tardes, Sergio" · "Sergio TV"), editables desde la tele.
- **Tus apps, solo las que eliges:** al principio salen las primeras 6; las demás quedan en Todas las apps.
  - En el inicio, deja presionado OK sobre una app para moverla con ◀ ▶, o quitarla con ▼.
  - En Todas las apps, deja presionado OK para ponerla o quitarla del inicio (las del inicio llevan ✓).
  - El botón **Agregar**, al final de la fila, lleva a Todas las apps.
- **Recientes:** las últimas apps que abriste, vengan del inicio, del control o de Gemini.
- **Reproductor:** lo que suena en Spotify, YouTube, etc., con anterior / pausa / siguiente, en el inicio y en el
  menú rápido.

### Menú rápido y protector

- **Menú rápido** al estilo Google TV (botón Ajustes o dejar presionado Inicio): protector de pantalla, Wi‑Fi,
  imagen, Bluetooth, sonido, accesibilidad, **temporizador para dormir** (15/30/60/90 min), apps, Deportes,
  En vivo, Personalizar y **Reiniciar**.
- **Protector de pantalla** a juego: paisaje, reloj grande que se mueve poco a poco, clima y la canción.

### Modo Sports (menú rápido → Deportes, apagado de fábrica)

- **Fila "Hoy"** con los partidos del día de las ligas que elijas (NFL, Liga MX, selecciones, NBA, MLB), con
  marcador en vivo.
- **Tu equipo:** el día que juega, el inicio se pinta con sus colores y muestra su logo, el rival, la hora, el
  canal y la cuenta regresiva; si gana, festeja. Los demás días, su botón abre su último partido y la tabla.
- **Panel del partido:** OK sobre un partido de "Hoy" abre marcador por periodo, líderes, comparativa de
  equipos, la tabla de su grupo y el próximo partido. ◀ ▶ pasan a los otros partidos; mientras se juega se
  actualiza cada 30 s. Si el partido va por una app de la tele (Prime Video, Disney+…), trae el botón para verlo.
- **Avisos** encima de cualquier app al empezar, al anotar y al terminar. Se pueden apagar, por si ves el partido
  con retraso y no quieres enterarte antes.
- Datos de ESPN (servicio público sin clave, no oficial: puede cambiar).

### En vivo (menú rápido → En vivo, apagado de fábrica)

- Una pestaña con los **canales gratis de Pluto TV** en filas: Destacados (TV abierta mexicana, noticias,
  NFL Channel…), Noticias, Deportes, Películas, Series, Novelas y más.
- Arriba, una **vista previa** del canal seleccionado: imagen, programa, descripción y cuánto falta.
- OK abre el canal en la app **Pluto TV** (instálala desde Play Store; si no está, XELEO te lleva ahí). XELEO
  solo muestra la guía, el video lo pone Pluto. La guía se precarga desde el inicio, así que la pestaña abre
  al instante.
- Guía de Pluto TV (pública, sin clave, no oficial: puede cambiar).

## Instalar

Primero, en la tele: Ajustes → Sistema → Acerca de → presiona 7 veces *Compilación del SO*; luego Opciones de
desarrollador → **Depuración USB** activada. Busca la IP de la tele en Ajustes → Red e Internet → tu red.

### Con un APK ya hecho

Si alguien te pasó `XELEO-TV.apk`, con [adb](https://developer.android.com/tools/releases/platform-tools)
(cambia la IP por la de tu tele; la tele pregunta "¿Permitir depuración USB?": acepta). Se instala con
`pm install` porque en Android 14 `adb install` se queda esperando una confirmación en pantalla; en Git Bash de
Windows, corre antes `export MSYS_NO_PATHCONV=1`.

```bash
adb connect 192.168.1.50:5555
adb push XELEO-TV.apk /data/local/tmp/xeleo.apk
adb shell pm install -r /data/local/tmp/xeleo.apk
adb shell cmd package set-home-activity --user 0 com.xeleo.tv/.HomeActivity
adb shell settings put secure enabled_accessibility_services com.xeleo.tv/.XeleoService
adb shell settings put secure accessibility_enabled 1
adb shell cmd notification allow_listener com.xeleo.tv/com.xeleo.tv.MediaListener
adb shell settings put secure screensaver_components com.xeleo.tv/com.xeleo.tv.SceneryDream
adb shell pm grant com.xeleo.tv android.permission.ACCESS_FINE_LOCATION
```

### Compilándolo tú

En la computadora (Git Bash en Windows), con JDK 17 y el SDK de Android:

```bash
./install.sh 192.168.1.50
```

El script compila, instala y deja XELEO TV como inicio, protector de pantalla y servicio del control.

### En los dos casos

En Google TV conviene desactivar también el inicio de Google, o se queda con el botón Inicio:

```bash
adb shell pm disable-user --user 0 com.google.android.apps.tv.launcherx
adb shell pm disable-user --user 0 com.google.android.tungsten.setupwraith
```

(`pm enable` con los mismos nombres los regresa.) Antes de desactivar `setupwraith`, revisa que tu red Wi‑Fi no
sea suya (`adb shell dumpsys wifi | grep -o 'cname=[^ ]*'`): si lo es, olvida la red y vuelve a conectarte.

Al terminar, apaga otra vez la Depuración USB. Luego, en el menú rápido: **Personalizar** (nombre y ciudad del
clima), **Deportes** (ligas y tu equipo) y **En vivo**, si los quieres.

## Cambiar cosas

- **Nombre, saludo y ciudad del clima:** en la tele, menú rápido → Personalizar. De fábrica no hay nombre
  ("Mi TV") y el clima es de Ciudad de México.
- **Apps del inicio por defecto:** `dock_apps` en `app/src/main/res/values/config.xml` (van primero la primera vez).
- **Probar otro clima en el paisaje** (para ver cómo se ve):
  `adb shell am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.WEATHER_PREVIEW --ei code 61`
  (61 lluvia, 95 tormenta, 45 neblina, 71 nieve, 2 nubes, -1 el clima real).
- **Ver un aviso de partido de ejemplo:**
  `adb shell am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.SPORTS_TEST`

## Notas

- Java puro, sin librerías. `minSdk 26`, `targetSdk 34`. El APK pesa unos 150 KB.
- La app necesita acceso a notificaciones (solo para saber qué se está reproduciendo) y un servicio de
  accesibilidad (botón Inicio largo, temporizador, reinicio, recientes y avisos de partidos); `install.sh` los
  activa.
- Fuente: [Outfit](https://github.com/Outfitio/Outfit-Fonts), licencia SIL OFL 1.1 (`docs/licencias/OFL-Outfit.txt`).
  Íconos: Material Icons de Google (Apache 2.0). Logos de equipos y canales: de ESPN y Pluto TV, se descargan al
  usarse y no vienen en el repo.

## Licencia

[MIT](LICENSE) © 2026 Sergio Escobedo. La fuente Outfit conserva su propia licencia (SIL OFL 1.1).
