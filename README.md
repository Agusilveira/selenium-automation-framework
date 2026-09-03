# selenium-automation-framework

[![tests](https://github.com/Agusilveira/selenium-automation-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/Agusilveira/selenium-automation-framework/actions/workflows/ci.yml)

Framework de automatización reutilizable sobre **Selenium 4**, con **TestNG** como
runner principal y **Cucumber** como camino opcional.

El producto es `src/main/java`: la librería. Los tests de `src/test/java` son la
demostración de que funciona, no el objetivo.

**52 clases de framework · UI, API y base de datos, cruzadas sobre una misma app · accesibilidad con línea base · CI en ocho jobs**

## Correrlo

```bash
git clone https://github.com/Agusilveira/selenium-automation-framework.git
cd selenium-automation-framework
mvn test
```

No hay drivers que descargar: Selenium Manager los resuelve en runtime.

```bash
mvn test -DsuiteXmlFile=src/test/resources/suites/smoke.xml      # camino crítico
mvn test -DsuiteXmlFile=src/test/resources/suites/parallel.xml   # 4 hilos
mvn test -DsuiteXmlFile=src/test/resources/suites/api.xml        # solo API, sin navegador
mvn test -DsuiteXmlFile=src/test/resources/suites/db.xml         # base de datos (requiere Docker)
mvn test -DsuiteXmlFile=src/test/resources/suites/grid.xml -Denv=grid   # contra el Grid
mvn test -DsuiteXmlFile=src/test/resources/suites/app.xml -Denv=app     # cruce entre capas
mvn test -Pcucumber                                              # los features
mvn test -Da11y.actualizar=true                                  # regenera las líneas base de accesibilidad
mvn test -DBROWSER=firefox -DTEST_ENV=ci                         # override de config
```

## Estructura

```
src/main/java/com/silveira/          EL FRAMEWORK
├── config/          ConfigManager · FrameworkConstants
├── driver/          DriverManager · BrowserFactory · TargetFactory
├── enums/           Browser · Target · Platform · FailureHandling
├── exceptions/      FrameworkException y 4 derivadas
├── api/             ApiClient · ApiResponse · AuthManager · ContractGuard · Paginador · ApiLogFilter · RateLimitFilter
├── a11y/            AnalisisA11y · LineaBaseA11y · ViolacionA11y
├── notifications/   ResumenDeCorrida · EmailNotifier
├── db/              DatabaseManager · DatabaseHelper · SqlLoader
├── keywords/        WebUI · WaitUtils · AlertUtils · FrameUtils · WindowUtils · TableUtils
├── helpers/         Properties · Locator · Json · Excel · File · Capture
├── utils/           Log · Date · FakeData · BrowserInfo
├── reports/         ExtentReportManager · ExtentTestManager · AllureManager
└── annotations/     FrameworkAnnotation

src/test/java/com/silveira/          QUIEN LO USA
├── common/          BaseTest · BaseApiTest · BaseDbTest · BaseAppTest
├── listeners/       TestListener · RetryAnalyzer · AnnotationTransformer · SoftFailureListener · FallbackGuardListener · NotificacionListener
├── dataprovider/    DataProviderManager
├── fixtures/        datos para otros tests: ProductosFixture (API) · ClientesFixture (base)
├── projects/        SauceDemo · the-internet · DummyJSON (API) · tienda (base) · app (las tres)
└── cucumber/        runner, steps y hooks sobre las mismas páginas

src/test/resources/
├── config/          un .properties por ambiente
├── suites/          smoke · regression · parallel · api · db · grid · app · cucumber · unit
├── objects/         locators externalizados
├── schemas/         JSON Schema de las respuestas
├── contracts/       contratos versionados de los endpoints
├── a11y/            línea base de accesibilidad por pantalla
├── sql/             esquema, datos y consultas fuera del codigo Java
└── data/            JSON y Excel para los DataProviders
```

## Decisiones de diseño

### `WebUI` es el corazón

La librería de acciones. Cada método espera lo que corresponde, ejecuta, deja
registro en el log y —en las acciones que pueden perderse— verifica que hayan
tenido efecto.

```java
inventario.agregarAlCarrito("Sauce Labs Backpack");
```

Detrás de eso: espera a que el botón sea clickeable, clickea, confirma que el
botón cambió a "Remove", reintenta si no pasó nada, y recurre a JavaScript
avisando si el evento no llega. Cuatro cosas en una línea. Esa es la diferencia
entre una capa de framework y un envoltorio.

### Todo recibe `By`, nunca `WebElement`

Un `WebElement` guardado se vuelve stale apenas la página se redibuja; un `By` se
resuelve recién al usarlo. Esa regla sola elimina una familia entera de fallos
intermitentes.

### Los locators viven fuera del código

En `objects/*.properties`, con formato `tipo:valor`:

```properties
login.boton=css:[data-test='login-button']
inventario.agregar=css:[data-test='add-to-cart-{0}']
```

Cambiar un selector no requiere recompilar ni saber Java. Y `{0}` permite
parametrizarlos: `LocatorHelper.by("inventario.agregar", "sauce-labs-backpack")`.
Las páginas del proyecto de ejemplo no declaran un solo `By`.

### Configuración con precedencia uniforme

`variable de entorno > propiedad de sistema > archivo del perfil > error`

Todas las claves admiten override, sin excepciones. Una clave que solo se puede
cambiar editando un archivo es una clave que no se puede cambiar desde el CI. Y
una clave faltante o mal tipada falla en el momento, con un mensaje que la nombra,
en vez de propagarse como `null` y explotar tres capas más abajo.

### `ThreadLocal` en `DriverManager`

Con TestNG y suites paralelas no hay contenedor que aísle por caso: cada hilo
necesita su propio driver. `remove()` en el teardown no es opcional — sin él, el
hilo del pool conserva la referencia a un driver cerrado y el test siguiente lo
recibe muerto.

### Un navegador por método, no por clase

Cuesta unos segundos por caso y elimina una categoría entera de fallos en cascada:
un test que deja el navegador en un estado raro no puede arruinar al siguiente.

### Implicit wait en cero

Toda la espera es explícita y vive en `WaitUtils`. Mezclar los dos mecanismos
produce tiempos impredecibles y difíciles de diagnosticar.

### Los tests no hablan con el reporte

Lo hace `TestListener`. Un caso se lee como lo que prueba, no como lo que
registra, y cambiar de herramienta de reporte no toca ni un test.

### Sin binarios de driver versionados

Selenium Manager los resuelve. Un `chromedriver.exe` commiteado deja de servir
apenas el navegador se actualiza.

### El recurso a JavaScript está medido, no escondido

En ciertos elementos, ningún input mediado por WebDriver llega a la página: ni
`element.click()`, ni `Actions.moveToElement().click()`, ni enfocar y mandar
ENTER. Se verificó instrumentando el documento con un listener propio, y no llega
ningún evento — mientras el elemento mide impecable: único que matchea el
selector, conectado al documento, habilitado, en el viewport, y `elementFromPoint`
lo devuelve a él. Solo la invocación directa por DOM funciona.

Se reprodujo en Windows y Linux, headless y con navegador visible, en máquina
local y en CI. No es del entorno ni del test.

Por eso `WebUI` recurre a JavaScript como último recurso. Y para que eso no se
convierta en una muleta cómoda, cada uso **se cuenta**, **aparece en el encabezado
del reporte** y hay un **umbral que rompe el build**. Si el número crece, la
respuesta es ver qué elemento nuevo lo necesita, no subir el umbral.

Esa métrica ya se pagó sola: mostró que la suite del Grid perdía 10 segundos
reintentando clicks que nunca iban a funcionar. Contar los reintentos exitosos en
unas 120 ejecuciones dio **cero**, así que la escalera bajó de 3 intentos a 2 y la
suite pasó de 34 a 24 segundos.

### Fallos tolerados que igual terminan en rojo

`WebUI` acepta una política por acción: `OPTIONAL` para lo que legítimamente puede
no estar (un banner de cookies), `CONTINUE_ON_FAILURE` para juntar varios fallos y
verlos todos de una en vez de arreglar de a uno.

`SoftFailureListener` da vuelta el resultado del caso a FAILURE si terminó con
fallos tolerados. Sin esa pieza, `CONTINUE_ON_FAILURE` sería una forma elegante de
esconder errores.

### UI y API separadas, con un puente explícito

Son dos capas con bases distintas: `BaseTest` levanta navegador, `BaseApiTest` no.
Los 17 casos de API corren en 6 segundos; forzarlos por la base de UI sería
levantar 17 Chrome para no usarlos.

Lo que las conecta es `fixtures/`, y la distinción importa:

| | La API es… | Vive en | Corre en |
|---|---|---|---|
| Tests de API | el sujeto bajo prueba | `projects/dummyjson/` | suite `api` |
| Fixtures | una herramienta para conseguir datos | `fixtures/` | los usan tests de UI |

Un test de UI llama a `ProductosFixture.algunos(3)` y no sabe que eso salió de
HTTP. Si mañana el dato viene de una base de datos, cambia el fixture y ningún
test se entera. Si la API no responde, el error dice que **falló una precondición**,
no que falló la aplicación bajo prueba.

### El intercambio HTTP siempre queda en el reporte

`ApiLogFilter` adjunta request y response completos a cada caso, sin que el test
pida nada. Cuando un test de API falla, eso es exactamente lo que hace falta y lo
único que evita tener que reproducirlo a mano.

Las cabeceras sensibles se enmascaran: un reporte de CI circula, y un token pegado
ahí es una credencial filtrada.

### Contratos versionados, no solo esquemas

El JSON Schema verifica que la respuesta tenga la forma esperada hoy.
`ContractGuard` responde otra pregunta: ¿sigue siendo compatible con la que había
cuando el contrato se acordó?

Y solo reporta lo que rompe de verdad. Que **aparezca** un campo no rompe a nadie:
quien no lo conoce lo ignora. Que **desaparezca**, o que **cambie de tipo**, rompe
a todos los que lo leían. El contrato vive versionado en `contracts/`, así que un
cambio deja rastro en el historial.

### La base de datos responde lo que una pantalla no puede

Un checkout que muestra "gracias por tu compra" pero no dejó la orden en la base
es un test de UI que pasa y un bug que se escapa. `DatabaseHelper` hace esas
preguntas: ¿el total de la orden coincide con la suma de sus ítems? ¿un cliente
dado de baja tiene pendientes? ¿un producto sin stock aparece en órdenes activas?

Corre contra un **Postgres real** levantado por Testcontainers, no contra una base
embebida: el dialecto, los tipos y el comportamiento transaccional son los de
producción. Un contenedor por suite, no por caso.

**La suite de base corre aparte porque requiere Docker.** `mvn test` sigue
funcionando sin él, así que la promesa de clonar y correr se mantiene.

Y qué pasa si Docker no está depende del perfil: **en local omite** con el motivo,
**en CI rompe el build**. Omitir en CI dejaría el job en verde sin haber probado
nada, que es la misma mentira que un `testFailureIgnore`. Los dos caminos se
verificaron rompiendo el arranque del contenedor a propósito.

### Los fixtures no dicen de dónde vienen los datos

```java
ProductosFixture.algunos(3);              // detrás hay HTTP
ClientesFixture.usuarioActivo();          // detrás hay SQL
```

Los dos exponen métodos en lenguaje de dominio, y quien los consume no sabe cuál
es cuál. Esa simetría es lo que hace que la abstracción sirva: cambiar la fuente
de un dato toca un archivo y ningún test.

`DatabaseHelper` devuelve `List<Map<String,String>>`, la misma forma que
`ExcelHelper`. Un `@DataProvider` puede pasar de leer una planilla a leer la base
sin que ningún test lo note.

### El mismo suite corre local o contra un Grid

```bash
docker compose -f docker-compose.grid.yml up -d
mvn test -DsuiteXmlFile=src/test/resources/suites/grid.xml -Denv=grid
```

Hub y nodos Chrome y Firefox en contenedores, con las versiones fijadas. Un
`latest` haría que la misma suite corra contra navegadores distintos según el
día, y cuando algo falla no se sabría si cambió el código o cambió el navegador.

Lo que cambia en los tests: nada. `TargetFactory` devuelve un `RemoteWebDriver`
en vez de un `ChromeDriver` y el resto del framework no se entera.

**Los nodos graban video de cada sesión.** Para fallos de timing, donde el
screenshot del final no dice qué pasó, es la diferencia entre diagnosticar y
adivinar. En CI los videos se publican como artefacto junto al reporte.

Vale ser honesto sobre el alcance: para 58 casos que corren en 90 segundos, el
Grid es **demostrativo, no necesario**. Su valor real acá fue otro — era lo único
que ejercitaba la rama `GRID` de `TargetFactory`, que compilaba desde el primer
día y no la corría nadie.

### El cruce entre capas

Es lo que el framework no podía demostrar hasta tener una aplicación cuyas tres
caras fueran suyas: SauceDemo no tiene API, DummyJSON no tiene interfaz, y a la
base de una app pública no se llega.

```bash
docker compose -f docker-compose.app.yml up -d
./scripts/preparar-app.sh
mvn test -DsuiteXmlFile=src/test/resources/suites/app.xml -Denv=app
```

Levanta Gitea con Postgres. **El código de la aplicación no vive en este
repositorio**: solo el compose que baja las imágenes y el script que la prepara.

Con eso, una acción hecha por un camino se verifica por los otros dos:

```java
int numero = AppApi.crearIssue(titulo, cuerpo).tieneCodigo(201).campo("number");

assertThat(issuesEnLaBaseConTitulo(titulo)).isEqualTo(1);   // la fila existe

app.ingresar();
assertThat(app.existeElIssueConTitulo(titulo)).isTrue();    // y se ve en pantalla
```

Por qué importa: un checkout que muestra "gracias por tu compra" pero no dejó la
orden en la base es un test de interfaz que pasa y un bug que llega a producción.
Ninguna de las tres capas por separado lo detecta.

El token de API lo genera el script y queda en un archivo ignorado por git. Una
credencial dentro del repositorio es una credencial filtrada, aunque la aplicación
corra en localhost.

### Accesibilidad que falla solo cuando empeora

`WebUI.verificarAccesibilidad("login")` inyecta **axe-core** y compara contra una
línea base versionada de esa pantalla.

```java
login.ingresarYEsperarInventario("standard_user", password());
inventario.agregarAlCarrito("Sauce Labs Backpack");

WebUI.verificarAccesibilidad("saucedemo-inventario", FailureHandling.CONTINUE_ON_FAILURE);
```

Que sea un método de `WebUI` y no una familia de casos aparte es la decisión que
importa: así se suma una línea a los casos que ya existen, y la pantalla se revisa
con sus datos y su estado reales en vez de vacía.

**El problema que resuelve la línea base.** Sumar accesibilidad a una aplicación
que ya existe encuentra decenas de violaciones legítimas y anteriores al cambio
que se está probando. Si eso hace fallar la suite, la suite queda roja el primer
día y el equipo aprende a ignorarla; si no hace fallar nada, la accesibilidad no
está probada. La salida es la misma que ya usa `ContractGuard`: guardar el estado
conocido y fallar únicamente cuando aparece una regla nueva o crece la cantidad de
elementos de una existente. Lo viejo se reporta siempre, y no rompe.

```properties
# src/test/resources/a11y/saucedemo-inventario.properties
select-name=1
```

Se versiona a propósito: es la única forma de que "esto ya estaba" sea verificable
y no la memoria de alguien. Se regenera con `mvn test -Da11y.actualizar=true`.

Una mejora nunca hace fallar, pero se avisa: si una regla baja de 3 a 1, la línea
base quedó más alta de lo necesario y una regresión posterior hasta ese número
pasaría desapercibida.

**Lo que axe no ve, y conviene decirlo.** Encuentra lo decidible mirando el DOM
—contraste, textos alternativos, roles ARIA, orden de encabezados— y ronda el 30%
de los problemas reales. Que el orden de tabulación tenga sentido, que un texto
alternativo describa la imagen, o que un lector de pantalla se entienda, no. Un
cero de axe no es una página accesible: es una página sin los errores que una
máquina puede ver sola.

### Notificaciones: el resumen no sabe por dónde se avisa

El notificador típico arma el texto del mail mientras recorre los resultados de
TestNG. Ahí el *qué pasó* y el *por dónde se avisa* quedan pegados: sumar otro
canal obliga a repetir el recorrido, y probar el formato del mensaje obliga a
levantar un servidor SMTP.

Acá `ResumenDeCorrida` es un valor y `EmailNotifier` uno de sus consumidores.

```java
ResumenDeCorrida resumen = ResumenDeCorrida.de(suite);
LogUtils.info(resumen.asunto());
EmailNotifier.notificar(resumen);
```

Cuatro decisiones, todas contra la versión ingenua:

- **Las credenciales salen del entorno y de ningún otro lado.** No hay un
  `mail.password` en el perfil ni un `get` que pueda caer en un archivo del
  repositorio. Si el framework no puede leer una contraseña de un archivo
  versionado, nadie la filtra ahí por accidente.
- **Sin configuración no hace nada, y eso no es un error.** Un `git clone && mvn
  test` no se rompe ni se cuelga porque falte un servidor SMTP.
- **Por defecto avisa solo cuando algo falló** (`MAIL_CUANDO=siempre|fallos|nunca`).
  Una notificación que llega siempre deja de leerse en una semana, y entonces
  tampoco se lee la que importa.
- **Nunca hace fallar la suite.** Con timeouts explícitos, y capturando también
  `LinkageError`: pintar de rojo una corrida verde porque el servidor de mail
  estaba caído es informar peor que no informar.

El cuerpo lleva los casos fallados con su primer mensaje y un enlace al reporte,
en vez del HTML de Extent adjunto: el adjunto pesa varios megas, muchos servidores
lo bloquean, y obliga a bajarlo para saber si hace falta mirarlo.

```bash
export MAIL_SMTP_HOST=smtp.gmail.com MAIL_SMTP_PUERTO=587
export MAIL_USUARIO=... MAIL_PASSWORD=...      # contraseña de aplicación
export MAIL_DESTINATARIOS=equipo@ejemplo.com
mvn test
```

Se verifica contra un SMTP en memoria (GreenMail) en un puerto libre, así que lo
que está probado es el camino entero —conexión, sobre, asunto, cuerpo— sin casilla
ni red.

## Cómo agregar algo

Cada paquete tiene un patrón, y agregar una pieza es seguirlo:

| Querés agregar | Va en | Mirá como ejemplo |
|---|---|---|
| Una acción de UI nueva | `keywords/WebUI` | cualquier método existente |
| Un tipo de espera | `keywords/WaitUtils` | `visible`, `urlContiene` |
| Una fuente de datos | `helpers/` + `dataprovider/` | `ExcelHelper` + `DataProviderManager` |
| Un navegador | `enums/Browser` + `driver/BrowserFactory` | el caso `EDGE` |
| Un destino de reporte | `reports/` | `AllureManager` |
| Un proyecto nuevo | `projects/<nombre>/` + `objects/<nombre>.properties` | `projects/theinternet` |
| Un endpoint a probar | `projects/<api>/tests/` + `schemas/` | `projects/dummyjson` |
| Un dato para tests de UI | `fixtures/` | `ProductosFixture` (API) · `ClientesFixture` (base) |
| Una consulta de verificacion | `sql/` + `projects/tienda/tests/` | `VerificacionesDbTest` |
| Un caso que cruza capas | `projects/app/` | `CruceDeCapasTest` |
| Accesibilidad de una pantalla | una línea en un caso que ya existe | `AccesibilidadTest` |
| Un canal de notificación | `notifications/` + `listeners/NotificacionListener` | `EmailNotifier` |

Lo que viene está en [ROADMAP.md](ROADMAP.md).

## Portfolio

Parte de una serie de repos de automatización:

| Repo | Stack | Estado |
|------|-------|--------|
| **selenium-automation-framework** (este) | Java · Selenium 4 · TestNG · Cucumber | ✅ |
| playwright-automation | TypeScript · Playwright | pendiente |
| cypress-automation | JavaScript · Cypress | pendiente |
