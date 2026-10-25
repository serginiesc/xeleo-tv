# XELEO TV para onn TV

**Español** · [English](README.en.md)

Pantalla de inicio para el **onn 4K Streaming Box** (Google TV), sin anuncios ni recomendaciones: todo lo que
aparece es lo que tú instalaste. Hecha en un onn 4K y probada también en un Chromecast con Google TV (HD), los
dos con Android 14; debería funcionar en otros aparatos con Google TV o Android TV, pero no se ha probado en ellos.

![Inicio de día con la fila Hoy de partidos y las apps](docs/screenshots/inicio-dia.jpg)

| De noche | Día de partido de tu equipo |
|---|---|
| ![Inicio de noche con el cielo nocturno](docs/screenshots/inicio-noche.jpg) | ![Día de partido de Santos: el inicio en sus colores, con rival, hora y la cuenta regresiva en grande](docs/screenshots/dia-de-partido.jpg) |

| Partido en vivo, de noche | Si gana tu equipo |
|---|---|
| ![Santos en vivo 2 a 1 contra Tijuana, con el estadio dibujado en sus colores y los reflectores encendidos](docs/screenshots/partido-en-vivo.jpg) | ![¡Ganaron! con confeti en los colores de Santos y el próximo partido](docs/screenshots/ganaron.jpg) |

| Panel del partido, en vivo | En vivo (Pluto TV y ViX) |
|---|---|
| ![Panel de América vs Atlas en juego: marcador, líderes, comparativa, tabla del Apertura y dónde verlo](docs/screenshots/panel-partido.jpg) | ![Pestaña En vivo en Deportes en vivo, con ViX Deportes y los canales de Pluto](docs/screenshots/en-vivo.jpg) |

| Menú rápido | Todas las apps |
|---|---|
| ![Menú rápido con la campana de notificaciones, Deportes y En vivo](docs/screenshots/menu-rapido.jpg) | ![Todas las apps, con palomita en las que están en el inicio](docs/screenshots/todas-las-apps.jpg) |

| Aviso sobre otra app | Mantener OK en un canal |
|---|---|
| ![Aviso de anotación de los Chiefs encima de otra app](docs/screenshots/aviso.jpg) | ![Menú de un canal: Mis canales, ocultar y recordar el siguiente programa](docs/screenshots/en-vivo-menu.jpg) |

| Acerca de y actualizaciones | Protector de pantalla |
|---|---|
| ![Acerca de: versión, aviso de versión nueva con el botón Actualizar y la licencia MIT](docs/screenshots/acerca-de.jpg) | ![Protector de pantalla de noche con reloj y clima](docs/screenshots/protector.jpg) |

## Qué trae

### El inicio

- **Barra de arriba fija** en todas las secciones: **Inicio, Apps y En vivo** son pestañas que cambian con solo
  pasar el selector (▼ entra, ▲ regresa a la pestaña en la que estás); **Gemini y Ajustes** se abren con OK.
- **Paisaje que sigue la hora:** amanecer, día, atardecer y noche, calculados con la salida y puesta del sol de tu
  ciudad. Si llueve, nieva, hay tormenta o neblina, el paisaje lo muestra (con lluvia y relámpagos animados).
- **Cielo vivo:** de noche las estrellas titilan y se desplazan despacio, con alguna estrella fugaz o un cometa de
  vez en cuando; de día pasan nubes y parvadas de pájaros. Se pausa en cuanto abres una app.
- **Clima** de tu ciudad en la línea de abajo (Open-Meteo, sin clave).
- **Saludo y nombre de la tele** ("Buenas tardes, Sergio" · "Sergio TV"), editables desde la tele.
- **Aro de luz** en la app seleccionada: una franja que le da la vuelta (con los colores de tu equipo el día
  que juega). Mientras te mueves por la fila el marco queda quieto; la luz arranca al detenerte.
- **Tus apps, solo las que eliges:** al principio salen las primeras 6; las demás quedan en Todas las apps.
  - En el inicio, deja presionado OK sobre una app para moverla con ◀ ▶, o quitarla con ▼.
  - En Todas las apps, deja presionado OK para ponerla o quitarla del inicio (las del inicio llevan ✓).
  - El botón **Agregar**, al final de la fila, lleva a Todas las apps.
- **Recientes:** las últimas apps que abriste, vengan del inicio, del control o de Gemini.
- **Reproductor:** lo que suena en Spotify, YouTube, etc., con anterior / pausa / siguiente, en el inicio y en el
  menú rápido.

### Menú rápido y protector

- **Menú rápido** al estilo Google TV (el botón de ajustes del control, o Ajustes en el inicio): protector de
  pantalla, Wi‑Fi, imagen, Bluetooth, sonido, accesibilidad, **temporizador para dormir** (15/30/60/90 min), apps,
  Deportes, En vivo, Personalizar y **Reiniciar**.
- **Notificaciones** (la campana del menú rápido, con cuántas hay; Ajustes en el inicio lleva un punto): sin el
  launcher de Google TV no hay otro lugar para verlas, y algunas cosas solo se ofrecen ahí, como la actualización
  del control. OK la abre, mantener OK la borra.
- **Protector de pantalla** a juego: paisaje, reloj grande que se mueve poco a poco, clima y la canción.

### Modo Sports (menú rápido → Deportes, apagado de fábrica)

- **Fila "Hoy"** con los partidos del día de las ligas que elijas, con marcador en vivo:
  - **México:** Liga MX.
  - **Estados Unidos:** NFL, NBA, MLB, NHL y MLS.
  - **Europa:** LaLiga, Premier League, Serie A, Bundesliga y Champions League.
  - **Selecciones:** todo el Mundial y, en amistosos, eliminatorias, Copa Oro, Nations League o Copa América, tu
    selección (cualquiera).
- **Tus equipos, uno por liga** (por ejemplo Chiefs en NFL, Santos en Liga MX y Real Madrid en LaLiga), y también
  en sus copas: el Real Madrid en la Champions, la Europa League y la Copa del Rey; Santos en la Concachampions y
  la Leagues Cup. El inicio sigue al que juega
  más pronto; los demás días dice cuál es tu próximo partido. En la fila Hoy, cada equipo tuyo que no juega
  trae su botón con su último partido y su tabla.
- **Día de partido:** el inicio se pinta con los colores de tu equipo y, a la derecha, pone una foto reciente de
  sus noticias en ESPN (cambia sola, sin notas tristes); si no hay ninguna, en la NFL un póster del QB titular en
  los colores del equipo con su número detrás, y en las demás ligas un estadio dibujado en sus colores (encendido
  de noche y con el escudo). En la mañana va suave; desde 3 horas antes, la cuenta regresiva en grande; durante el
  juego, el marcador grande (se actualiza cada 30 s); si gana, "¡Ganaron!" con confeti hasta la mañana siguiente.
  Si pierde, el inicio vuelve a la normalidad. OK sobre el marcador abre el panel del partido. La foto se apaga en
  Ajustes > Deportes.
- **Panel del partido:** OK sobre un partido de "Hoy" abre marcador por periodo, líderes, comparativa de
  equipos, la tabla de su grupo y el próximo partido. ◀ ▶ pasan a los otros partidos; mientras se juega se
  actualiza cada 30 s.
- **Dónde verlo:** en el panel, los canales que da ESPN y por dónde suele ir en México esa liga (ViX, TUDN,
  Azteca 7, Canal 5, ESPN/Disney+, Fox…), con el botón para abrir la app si la tienes.
- **Avisos** encima de cualquier app 15 minutos antes, al empezar, al anotar y al terminar. Se pueden apagar, por
  si ves el partido con retraso y no quieres enterarte antes.
- Datos de ESPN (servicio público sin clave, no oficial: puede cambiar).

### En vivo (menú rápido → En vivo, apagado de fábrica)

- Una pestaña con los **canales gratis de Pluto TV y ViX**, con un botón por categoría y cuántos canales tiene
  ("Noticias · 7", "Deportes en vivo · 11", "ViX · 10"…). Basta pasar por un botón para ver sus canales.
- **Deportes en vivo:** los deportivos de Pluto (NFL Channel, Azteca Deportes, UFC…) junto con ViX Deportes 24/7.
- **ViX:** sus canales gratis (N+, N+ Foro, ViX Deportes, novelas…); OK abre la app de ViX en ese canal. ViX no
  publica su programación, así que esos no traen horario.
- **Mis canales:** el primer botón. De fábrica trae TV abierta mexicana, noticias y NFL Channel; deja presionado
  OK sobre cualquier canal para agregarlo o quitarlo (los tuyos llevan ★).
- **Ocultar canales** que no te interesan, con el mismo OK sostenido; quedan en el botón "Ocultos" por si los
  quieres de vuelta.
- **Recordatorios:** en ese mismo menú, "Recordar" el siguiente programa del canal; 5 minutos antes sale un aviso
  encima de lo que estés viendo.
- Arriba, una **vista previa** del canal seleccionado: imagen, programa, descripción y cuánto falta.
- OK abre el canal en la app **Pluto TV** o **ViX** (instálalas desde Play Store; si no están, XELEO te lleva ahí). XELEO
  solo muestra la guía, el video lo pone Pluto. La guía se precarga desde el inicio, así que la pestaña abre
  al instante.
- Guía de Pluto TV (pública, sin clave, no oficial: puede cambiar). Los anuncios al entrar a un canal son de
  Pluto o ViX; XELEO no los controla.

### Actualizaciones

- XELEO se **actualiza solo desde GitHub**: cada 6 horas revisa la versión de Releases; si hay una nueva,
  **Ajustes** muestra un punto y en menú rápido → **Acerca de** aparece **Actualizar**. Descarga el APK y Android
  pide un solo OK en pantalla (una app no puede actualizarse sin preguntar). La primera vez Android puede pedir
  permitir «Instalar apps desconocidas» para XELEO TV; `install.sh` ya lo deja permitido.

### Sin internet y al reinstalar

- **Sin internet** (por ejemplo, la tele recién prendida y el Wi‑Fi aún conectando), el inicio muestra los partidos
  del día, el clima y la guía de En vivo que tenía guardados, y los actualiza en cuanto hay red.
- **Respaldo de ajustes:** tu nombre, ciudad, apps del inicio, Deportes, Mis canales y recordatorios se guardan en
  un archivo. `install.sh` lo copia a la computadora antes de instalar y lo regresa si la instalación fue desde
  cero; también entra en el respaldo de Google de la tele.

## Instalar

Funciona en el **onn 4K** y en el **Chromecast con Google TV** (probado en el HD, Android 14). Lo que se puede
**sin computadora**: instalar el APK (con la app Downloader o una memoria USB), encender sus servicios en los
ajustes de la tele (Accesibilidad → XELEO TV, Acceso especial de apps → Acceso a notificaciones, Sistema →
Protector de pantalla) y actualizarlo desde Acerca de. Lo que **necesita computadora, una sola vez**: que XELEO
sea lo que abre el botón Inicio. Google TV no tiene opción para cambiar el inicio, así que hace falta adb.

### 1. Prende la depuración en la tele

Ajustes → Sistema → Acerca de → presiona 7 veces *Compilación del SO*. Luego, en Opciones de desarrollador:

- **onn:** **Depuración USB** activada. Basta la IP de la tele (Ajustes → Red e Internet → tu red).
- **Chromecast:** la Depuración USB sola no abre la conexión por red; usa **Depuración inalámbrica**. Ahí mismo
  verás la IP:puerto para conectar, y en *Vincular dispositivo con código de vinculación* otra IP:puerto y un código
  de 6 números para vincular la computadora (una sola vez). El puerto cambia cada vez que se vuelve a prender.

### 2. Instálalo con install.sh

En la computadora (Git Bash en Windows), con las
[platform-tools](https://developer.android.com/tools/releases/platform-tools) de Android. Con `--apk` usa el APK
de [Releases](https://github.com/serginiesc/xeleo-tv/releases/latest), sin compilar nada; sin `--apk` lo compila
(necesita JDK 17 y el SDK).

```bash
./install.sh 192.168.1.50 --apk XELEO-TV.apk --replace-google-home
```

Para el Chromecast, la primera vez agrega el vínculo (la IP:puerto y el código de la pantalla de vincular; la
tele es la IP:puerto de la pantalla de Depuración inalámbrica):

```bash
./install.sh 192.168.1.60:37253 --pair 192.168.1.60:46101 189303 --apk XELEO-TV.apk --replace-google-home
```

El script instala XELEO y lo deja como inicio, protector de pantalla y servicio del control, con acceso a
notificaciones. `--replace-google-home` apaga además el inicio de Google TV y su asistente de configuración, **los
dos en un solo paso**: si solo se apaga el inicio, el asistente se queda con el botón Inicio (pantalla negra) y en
el Chromecast la Depuración inalámbrica se cae antes de terminar. `./install.sh <tele> --restore-google-home` los
regresa.

Antes de instalar guarda tus ajustes en `respaldo-tv-<IP>.json` y, si tuvo que instalar desde cero (por ejemplo,
si la versión anterior estaba firmada con otra llave), los regresa solo. También sirve con el emulador:
`./install.sh emulator-5554`. Las versiones que compila van firmadas con la llave de XELEO TV si existe
`keystore.properties` (no está en el repo); si no, con la llave de depuración de tu computadora.

### A mano, sin el script

Con adb conectado a la tele (`adb connect IP:puerto`; en Git Bash de Windows, antes `export MSYS_NO_PATHCONV=1`).
Se instala con `pm install` porque en Android 14 `adb install` se queda esperando una confirmación en pantalla.

```bash
adb push XELEO-TV.apk /data/local/tmp/xeleo.apk
adb shell pm install -r /data/local/tmp/xeleo.apk
adb shell settings put secure enabled_accessibility_services com.xeleo.tv/.XeleoService
adb shell settings put secure accessibility_enabled 1
adb shell cmd notification allow_listener com.xeleo.tv/com.xeleo.tv.MediaListener
adb shell settings put secure screensaver_components com.xeleo.tv/com.xeleo.tv.SceneryDream
adb shell pm grant com.xeleo.tv android.permission.ACCESS_FINE_LOCATION
adb shell "pm disable-user --user 0 com.google.android.apps.tv.launcherx; pm disable-user --user 0 com.google.android.tungsten.setupwraith; cmd role add-role-holder android.app.role.HOME com.xeleo.tv"
```

La última línea va completa, entre comillas: así corre entera en la tele aunque la conexión se caiga a la mitad.
`pm enable` con los mismos nombres los regresa.

### Si algo sale mal

- **Pantalla negra al picar Inicio:** se apagó el inicio de Google pero no su asistente. Vuelve a entrar a la
  tele (botón del Asistente y di "Abre configuración", o desconéctala 10 segundos) y corre otra vez con
  `--replace-google-home`, o regrésalo todo con `--restore-google-home`.
- **El Chromecast deja de responder a adb:** la Depuración inalámbrica se apagó o cambió de puerto; vuelve a
  prenderla y usa la IP:puerto nueva (el vínculo ya hecho sigue sirviendo).

### Al terminar

Apaga la depuración otra vez. Luego, en el menú rápido: **Personalizar** (nombre y ciudad del clima),
**Deportes** (ligas y tus equipos) y **En vivo**, si los quieres. El control del onn tiene botón de ajustes, que
abre el menú de XELEO; el del Chromecast no, así que ahí el menú se abre con Inicio → Ajustes.

## Cambiar cosas

- **Nombre, saludo y ciudad del clima:** en la tele, menú rápido → Personalizar. De fábrica no hay nombre
  ("Mi TV") y el clima es de Ciudad de México.
- **Apps del inicio por defecto:** `dock_apps` en `app/src/main/res/values/config.xml` (van primero la primera vez).
- **Probar otro clima en el paisaje** (para ver cómo se ve):
  `adb shell am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.WEATHER_PREVIEW --ei code 61`
  (61 lluvia, 95 tormenta, 45 neblina, 71 nieve, 2 nubes, -1 el clima real).
- **Ver un aviso de partido de ejemplo:**
  `adb shell am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.SPORTS_TEST`
- **Ver el día de partido con tu equipo** (`morning`, `soon`, `live`, `won`; `off` vuelve al real; `--ez photo
  false` solo colores, `--ez poster true` el póster o el estadio en vez de la foto, `--es league ligamx` tu equipo de esa liga):
  `adb shell am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.GAMEDAY_PREVIEW --es moment live`
- **Ver un recordatorio de ejemplo** (sale en unos segundos):
  `adb shell am broadcast -n com.xeleo.tv/.ControlReceiver -a com.xeleo.tv.REMINDER_TEST`
- **Guardar o restaurar los ajustes a mano:** `-a com.xeleo.tv.BACKUP` escribe
  `/sdcard/Android/data/com.xeleo.tv/files/xeleo-respaldo.json`; `-a com.xeleo.tv.RESTORE` los toma de ahí.

## APK automático (para quien mantiene el repo)

`.github/workflows/apk.yml` compila el APK con cada cambio en `main` y lo publica como la versión "ultima" en
Releases, firmado con la llave de XELEO TV. La llave vive en los secretos del repo: `XELEO_KEYSTORE_B64` (el
`.jks` en base64), `XELEO_KEYSTORE_PASSWORD`, `XELEO_KEY_ALIAS` y `XELEO_KEY_PASSWORD`. Sin ellos, la compilación
avisa y se detiene. El número de versión es la cantidad de commits (1.12, 1.13…).

## Notas

- Java puro, sin librerías. `minSdk 26`, `targetSdk 34`. El APK pesa unos 170 KB.
- La app necesita acceso a notificaciones (para saber qué se está reproduciendo y para el panel de
  notificaciones) y un servicio de accesibilidad (el botón de ajustes del control, temporizador, reinicio,
  recientes y avisos de partidos); `install.sh` los activa.
- Fuente: [Outfit](https://github.com/Outfitio/Outfit-Fonts), licencia SIL OFL 1.1 (`docs/licencias/OFL-Outfit.txt`).
  Íconos: Material Icons de Google (Apache 2.0). Logos de equipos y canales, y las fotos del día de partido: de ESPN y
  Pluto TV, se descargan al usarse y no vienen en el repo.

## Licencia

[MIT](LICENSE) © 2026 Sergio Escobedo. La fuente Outfit conserva su propia licencia (SIL OFL 1.1).

Si haces tu propia versión, la licencia te pide conservar este aviso y el nombre del autor; XELEO lo muestra en
menú rápido → **Acerca de**, así que basta con no quitarlo.
