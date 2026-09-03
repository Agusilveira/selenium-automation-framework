# selenium-automation-framework

[![tests](https://github.com/Agusilveira/selenium-automation-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/Agusilveira/selenium-automation-framework/actions/workflows/ci.yml)

Framework de automatización reutilizable sobre **Selenium 4**, con **TestNG** como
runner principal y **Cucumber** como camino opcional.

El producto es `src/main/java`: la librería. Los tests de `src/test/java` son la
demostración de que funciona, no el objetivo.

**41 clases de framework · UI y API · 58 casos de UI y 17 de API · suites paralelas · CI en dos navegadores**

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
mvn test -Pcucumber                                              # los features
mvn test -DBROWSER=firefox -DTEST_ENV=ci                         # override de config
```

## Estructura

```
src/main/java/com/silveira/          EL FRAMEWORK
├── config/          ConfigManager · FrameworkConstants
├── driver/          DriverManager · BrowserFactory · TargetFactory
├── enums/           Browser · Target · Platform · FailureHandling
├── exceptions/      FrameworkException y 4 derivadas
├── api/             ApiClient · ApiResponse · AuthManager · ContractGuard · Paginador · ApiLogFilter
├── keywords/        WebUI · WaitUtils · AlertUtils · FrameUtils · WindowUtils · TableUtils
├── helpers/         Properties · Locator · Json · Excel · File · Capture
├── utils/           Log · Date · FakeData · BrowserInfo
├── reports/         ExtentReportManager · ExtentTestManager · AllureManager
└── annotations/     FrameworkAnnotation

src/test/java/com/silveira/          QUIEN LO USA
├── common/          BaseTest · BaseApiTest
├── listeners/       TestListener · RetryAnalyzer · AnnotationTransformer
├── dataprovider/    DataProviderManager
├── fixtures/        datos para tests de UI, obtenidos por API
├── projects/        SauceDemo · the-internet · DummyJSON (API)
└── cucumber/        runner, steps y hooks sobre las mismas páginas

src/test/resources/
├── config/          un .properties por ambiente
├── suites/          smoke · regression · parallel · api · cucumber · unit
├── objects/         locators externalizados
├── schemas/         JSON Schema de las respuestas
├── contracts/       contratos versionados de los endpoints
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
del reporte** y hay un **umbral que rompe el build**. En la última corrida de CI:
Chrome lo necesitó 7 veces, Firefox ninguna. Si el número crece, la respuesta es
ver qué elemento nuevo lo necesita, no subir el umbral.

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
| Un dato para tests de UI | `fixtures/` | `ProductosFixture` |

Lo que viene está en [ROADMAP.md](ROADMAP.md).

## Portfolio

Parte de una serie de repos de automatización:

| Repo | Stack | Estado |
|------|-------|--------|
| **selenium-automation-framework** (este) | Java · Selenium 4 · TestNG · Cucumber | ✅ |
| playwright-automation | TypeScript · Playwright | pendiente |
| cypress-automation | JavaScript · Cypress | pendiente |
