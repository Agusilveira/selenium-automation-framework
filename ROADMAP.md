# ROADMAP

Lo que el framework todavía no hace, con dónde se enchufa cada cosa. El orden es
por relación entre valor y esfuerzo, no por preferencia.

Nada de esto es un pendiente urgente: la v1 funciona completa sin ellos. Es el
mapa de por dónde crece.

---

## Telegram

**Paquete:** `notifications/` (ya existe) · **Toca:** `listeners/NotificacionListener`

El mail ya está, y con él la parte difícil: `ResumenDeCorrida` no sabe por dónde
se avisa. Sumar Telegram es una clase que recibe ese resumen y una línea en el
listener. Token y chat por variable de entorno, como las credenciales de SMTP.

## Visual regression

**Paquete:** `visual/` nuevo

Comparación de screenshots contra una línea base para detectar cambios visuales
que ningún assert funcional captura. Necesita una estrategia de tolerancia y de
actualización de la base, que es la parte difícil, no la comparación.

`LineaBaseA11y` ya resolvió la mitad conceptual: archivo versionado, se falla solo
por lo que empeoró, se regenera con una propiedad. Lo que no aporta es la
tolerancia, que en imágenes no es un entero sino un umbral de píxeles distintos, y
ahí es donde esto se pone difícil de verdad.

## Appium

**Toca:** `enums/Platform` (ya existe), `driver/`

Mobile sobre la misma estructura. `Platform` ya está previsto, pero el salto es
más grande de lo que parece: los locators, las esperas y el ciclo de vida del
driver son distintos. Probablemente amerite un `MobileUI` hermano de `WebUI` en
vez de forzar una sola abstracción para los dos mundos.

---

## Deuda conocida

- **`Platform` no se usa todavía.** Solo tiene sentido con Grid o Appium, que
  están más arriba en esta lista. Se deja declarado porque `TargetFactory` ya
  contempla `Target.GRID` y ahí es donde va a hacer falta.

- **`clickHasta` reintenta una vez.** Verifica que el disparador siga presente
  antes de reintentar, así que no repite una acción que ya ocurrió. Pero si un
  botón sigue visible después de un click que tuvo efecto parcial, el reintento es
  posible. Para acciones no idempotentes conviene pasar un efecto inequívoco, como
  un cambio de URL.

- **El único reintento que queda no tiene evidencia a favor.** En las mediciones
  ninguno rescató un click. Se dejó como seguro ante condiciones de red que no
  probamos, y el log avisa si alguna vez sirve: si "necesitó 2 intentos" nunca
  aparece, se puede bajar a 1 y ahorrar otros 4 segundos por click afectado.

---

## Resuelto

Lo que estaba en esta sección y se cerró, con lo que se aprendió en el camino.

### Accesibilidad con línea base

`a11y/` inyecta axe-core y compara contra una línea base versionada por pantalla.
`WebUI.verificarAccesibilidad("login")` se suma como una línea a un caso que ya
existe, que es la única forma de que la pantalla se revise con datos y estado
reales.

La decisión de fondo fue la misma que ya había tomado `ContractGuard` sin que yo
lo notara al empezar: **fallar solo por lo que empeoró**. Una aplicación que ya
existe tiene violaciones legítimas y anteriores al cambio que se prueba; hacerlas
fallar deja la suite roja el primer día, y no hacer fallar nada equivale a no
probar. Guardar el estado conocido resuelve las dos.

La línea base se sabotea a propósito en `LineaBaseA11yTest` —regla nueva, regla
que crece, regla que mejora— y además se verificó sobre el navegador borrando
`select-name` del archivo real: el caso falló nombrando la regla, su impacto y el
enlace a la documentación.

Lo primero que encontró sobre SauceDemo fue un `select-name` **crítico**: el
desplegable de ordenamiento del listado no tiene nombre accesible. La página de
login, en cambio, pasa limpia.

Queda dicho en el README lo que axe no puede ver, porque un cero de axe se lee muy
fácil como "la página es accesible" y no lo es: cubre alrededor del 30% de los
problemas reales, los que se deciden mirando el DOM.

### Notificaciones por mail

`notifications/` con `ResumenDeCorrida` (qué pasó) y `EmailNotifier` (por dónde se
avisa), separados a propósito: sumar otro canal no vuelve a recorrer los
resultados de TestNG, y probar el formato del mensaje no necesita un SMTP.

Va en un `ISuiteListener` y no en el `onFinish` de `TestListener`: ese corre una
vez por bloque `test` del XML, y la regresión tiene tres. Serían tres mails de la
misma corrida.

**El test encontró dos cosas que el diseño no.** Verificar el envío contra un SMTP
en memoria destapó un choque de classpath: `json-schema-validator` arrastra
`com.sun.mail:mailapi`, de la era `javax.mail`, cuyo `META-INF/mailcap` registra
manejadores que `jakarta.activation` intenta cargar al armar el mail.

Lo obvio era excluir el jar, y **la exclusión rompió la validación contra JSON
Schema**: la librería inicializa su tabla de formatos de golpe y uno de ellos
referencia `javax.mail.internet`. Se revirtió. La convivencia se resuelve
nombrando el manejador correcto por programa, que tiene prioridad sobre lo leído
de archivos, y queda en una línea dentro de `EmailNotifier` en vez de en el árbol
de dependencias de todos.

Lo segundo: el `NoClassDefFoundError` es un `Error`, no un `RuntimeException`, así
que atravesaba el `catch` y se llevaba puesta la suite entera. Un notificador que
promete no afectar la corrida tiene que cumplirlo también cuando el problema es el
classpath.

### Un test que medía el tamaño de página

Salió de correr la suite del cruce entre capas contra una base que había quedado
con varias corridas encima, cosa que en CI nunca pasa porque arranca vacía.

`lasTresCapasCoincidenEnElConteo` comparaba **las filas visibles del listado**
contra el `COUNT` de la base. Con pocos issues coincidían y el caso pasaba; con 23
la interfaz mostraba 20, porque pagina de a 20. El test no medía el total, medía
el tamaño de página, y llevaba así desde que se escribió.

Arreglarlo bajando la comparación a "la interfaz muestra al menos" habría sido
esconderlo. Lo que corresponde es que **cada capa dé su total de la forma en que
esa capa sabe darlo**: la base con un `COUNT`, la API con la cabecera
`X-Total-Count` —pedir un elemento alcanza—, y la interfaz con el contador de la
pestaña, que es lo que la aplicación le afirma a una persona. Las tres son exactas
y ninguna depende de cuántos entren en una pantalla.

De paso quedó una capacidad que faltaba: `ApiResponse.cabecera(...)`. No todo lo
que hay que afirmar está en el cuerpo —totales de paginación, límites de
peticiones, identificadores de correlación—, y hasta ahora había que bajar al
`Response` de RestAssured para leerlo.

### La aplicación propia y el cruce entre capas

`docker-compose.app.yml` levanta Gitea con Postgres: interfaz, API REST y base del
mismo sistema. `preparar-app.sh` la deja usable sin pasos manuales.

Era el último límite anotado, y el único que no se podía sortear buscando algo
público: hay demos con interfaz y API, pero **ninguna expone su base de datos**, y
con razón. Para preguntarle algo a la base hay que ser quien la corre.

Nada del código de la aplicación entra al repositorio: solo la receta para
levantarla.

Los tres fallos que tuvo la primera corrida fueron todos de condiciones de espera
mal elegidas, y dos merecen quedar anotados:

**Esperar la URL equivocada.** Tras el login, Gitea redirige al panel de inicio y
no al perfil. Lo resolvió el mensaje de error que `clickHasta` ya traía: *"el click
tuvo efecto pero el resultado esperado nunca llegó, quedó en /"*. De ahí salieron
`hastaQueLaUrlNoContenga`, para cuando importa haber salido de una página y no a
cuál se llegó.

**Una condición que se cumplía antes de empezar.** Crear un issue desde
`/issues/new` esperando que la URL contenga `/issues/`: ya lo contenía. La espera
pasaba al instante y el test creía que la acción había ocurrido. De ahí salió
`hastaQueLaUrlCoincidaCon`, con expresión regular.

Las dos son ahora capacidades del framework, no parches del proyecto.

### El costo del recurso a JavaScript

Sobre el Grid, `compraCompleta` tardaba 27 segundos y la suite 34. El
`FallbackTracker` mostró dónde se iban: los botones del checkout necesitan
JavaScript el 100% de las veces, y el framework gastaba 3 intentos de 4 segundos
en cada uno antes de rendirse.

**La primera solución fue la equivocada, y medirla lo demostró.** Implementé una
memoria por corrida —"este locator ya necesitó JavaScript, no vuelvas a esperar"—
y el resultado fue **cero mejora**: 34 segundos antes, 34 después. El motivo, que
el propio tracker delató: los elementos caros aparecen **una sola vez por corrida**
(5 usos sobre 2 elementos distintos), así que una memoria dentro de la corrida no
tiene sobre qué actuar. Se revirtió.

El dato que sí resolvió el problema salió de contar reintentos exitosos en unas
120 ejecuciones: **ninguno**. Nunca un click se recuperó en el segundo o tercer
intento. La escalera era el desperdicio, no la falta de memoria.

Bajar `INTENTOS_ACCION` de 3 a 2 llevó la suite de 34 a 24 segundos, sin romper
un solo test. Una línea de configuración, respaldada por una medición, después de
haber descartado una solución más elaborada que no servía.

### Selenium Grid en Docker

`docker-compose.grid.yml` con hub y nodos Chrome y Firefox, perfil `grid`, suite
propia y job de CI.

Su valor no fue el que le había atribuido. Escribí que "destraba el cruce completo
entre capas", y eso era falso: el cruce lo destraba tener una app cuyas tres caras
sean nuestras, no el Grid. Eran dos ítems distintos mezclados bajo un título.

Lo que sí aportó: era **lo único que ejercitaba la rama `GRID` de
`TargetFactory`**, que compilaba desde el primer día sin que la corriera nadie —
la misma deuda que tenían `FrameUtils` y `TableUtils`. Pasó a la primera, cosa que
no esperaba después de los tres bugs anteriores.

Y cerró de paso el ítem de **grabación de pantalla**: los nodos de Selenium graban
video de cada sesión, así que no hizo falta escribir un grabador.

Para 58 casos que corren en 90 segundos, el Grid es demostrativo y no necesario.
Está bien que lo sea, pero conviene decirlo.

### Base de datos

`db/` con `DatabaseManager` (pool), `DatabaseHelper` (PreparedStatement,
transacciones, conteos) y `SqlLoader`, corriendo contra un Postgres real
levantado por Testcontainers.

Dos decisiones que salieron de probar los caminos, no de suponerlos:

**La suite corre aparte.** Requiere Docker, y el resto del framework no. Meterla
en la regresión habría roto la promesa de `git clone && mvn test`.

**Omitir en CI habría sido una mentira.** La primera versión omitía los casos si
Docker faltaba, en todos los entornos. Al probarlo salió el problema: en CI eso
deja el job en verde sin haber probado nada, igual que un `testFailureIgnore`.
Ahora depende del perfil, y los dos caminos están verificados rompiendo el
arranque del contenedor a propósito.

`ClientesFixture` cierra el patrón que abrió `ProductosFixture`: uno trae datos
por SQL y el otro por HTTP, y quien los consume no distingue.

### Testing de API

`api/` sobre RestAssured, con `ApiClient`, `AuthManager` (token por hilo),
`ApiResponse` con aserciones que incluyen el cuerpo en el mensaje de fallo,
validación contra JSON Schema, `Paginador` y `ContractGuard`.

Dos decisiones que definieron la forma:

**La capa no envuelve RestAssured por envolverlo.** Lo que agrega es que cada
intercambio HTTP quede en el reporte automáticamente, con las cabeceras sensibles
enmascaradas. Cuando un test de API falla, eso es lo único que importa.

**Los tests de API y los fixtures son cosas distintas aunque compartan
transporte.** En `projects/dummyjson/` la API es el sujeto bajo prueba; en
`fixtures/` es una herramienta para conseguir datos. Un test de UI pide
`ProductosFixture.algunos(3)` y no sabe que salió de HTTP, así que cambiar la
fuente no toca ningún test.

`ContractGuard` se verificó saboteando un contrato a propósito: detectó el campo
faltante y el cambio de tipo con el mensaje exacto. Un guardián que nunca falló no
está probado.

### `FailureHandling` operativo

Era un enum declarado que nadie usaba. Ahora `WebUI` tiene sobrecargas que
aplican la política, `SoftFailures` acumula los fallos tolerados por hilo y
`SoftFailureListener` da vuelta el resultado del caso a FAILURE si terminó con
alguno.

Esa última pieza es la que importa: sin ella, `CONTINUE_ON_FAILURE` habría sido
una forma elegante de esconder errores. Va en `afterInvocation` de
`IInvokedMethodListener` y no en un `@AfterMethod` porque corre antes de
`onTestSuccess` —así el reporte ya lo ve fallado— y porque un `@AfterMethod` que
lanza marca un fallo de configuración, no del test.

### El recurso a JavaScript, medido

Estaba como "avisa pero no falla". Se investigó a fondo antes de decidir qué
hacer, y el resultado cambió la conclusión.

En ciertos elementos, **ningún input mediado por WebDriver llega a la página**:
ni `element.click()`, ni `Actions.moveToElement().click()`, ni enfocar y mandar
ENTER. Instrumentando el documento con un listener propio, no llega ningún
evento. Y mientras tanto el elemento mide impecable: único que matchea el
selector, conectado al documento, habilitado, dentro del viewport, sin scroll, y
`elementFromPoint` lo devuelve a él tanto en el centro como en la esquina. Solo
la invocación directa por DOM funciona.

Se reprodujo en Windows y en Linux, con navegador visible y headless, en máquina
local y en CI, con frecuencia variable (~70% headless, ~33% headed).

O sea que el recurso a JavaScript no tapa un bug propio: rodea una limitación
real. Por eso queda habilitado en todos los perfiles, pero **contado**
(`FallbackTracker`), **visible en el encabezado del reporte** y **con un umbral
que rompe el build** vía `FallbackGuardTest`. Si el número crece, la respuesta es
ver qué elemento nuevo lo necesita, no subir el umbral.

El guardián es un caso de test y no una excepción desde el listener: una
excepción en `ISuiteListener` rompe el build pero deja a surefire sin poder
informar cuántos tests corrieron, y el resultado es un rojo que no dice nada.

### Segundo proyecto

`FrameUtils`, `TableUtils` y `WindowUtils` compilaban pero **nunca se habían
ejecutado**. El proyecto sobre the-internet los ejercita, y de paso valida que el
framework sirva para una app sin login, sin flujo y con otra estructura.

Encontró un bug real en `clickHasta` a los cinco minutos de existir: la ventana
de espera del efecto era más corta que una carga diferida legítima de la página,
así que el método reintentaba un click que ya había funcionado. Sobre un botón de
compra eso genera una orden duplicada. Ahora `clickHasta` verifica que el
disparador siga presente antes de reintentar, y acepta un tiempo de efecto
explícito para los casos legítimamente lentos.

Es exactamente el argumento a favor de tener un segundo proyecto: no encontró un
problema del proyecto nuevo, encontró uno del framework.
