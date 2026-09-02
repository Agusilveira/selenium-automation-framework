# selenium-automation-framework

[![tests](https://github.com/Agusilveira/selenium-automation-framework/actions/workflows/ci.yml/badge.svg)](https://github.com/Agusilveira/selenium-automation-framework/actions/workflows/ci.yml)

Framework de automatización reutilizable sobre **Selenium 4**, con **TestNG** como
runner principal y **Cucumber** como camino opcional.

El producto es `src/main/java`: la librería. Los tests de `src/test/java` son la
demostración de que funciona, no el objetivo.

**34 clases de framework · 43 casos de ejemplo · suites paralelas · CI en dos navegadores**

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
├── keywords/        WebUI · WaitUtils · AlertUtils · FrameUtils · WindowUtils · TableUtils
├── helpers/         Properties · Locator · Json · Excel · File · Capture
├── utils/           Log · Date · FakeData · BrowserInfo
├── reports/         ExtentReportManager · ExtentTestManager · AllureManager
└── annotations/     FrameworkAnnotation

src/test/java/com/silveira/          QUIEN LO USA
├── common/          BaseTest
├── listeners/       TestListener · RetryAnalyzer · AnnotationTransformer
├── dataprovider/    DataProviderManager
├── projects/        proyecto de ejemplo: páginas y casos
└── cucumber/        runner, steps y hooks sobre las mismas páginas

src/test/resources/
├── config/          un .properties por ambiente
├── suites/          smoke · regression · parallel · cucumber · unit
├── objects/         locators externalizados
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

### Configuración de Chrome para CI

Conocimiento pagado a fuerza de diagnóstico en un proyecto anterior: en runners de
CI, Chrome headless a veces no entrega los eventos de entrada. Selenium no lanza
nada, el elemento está visible y habilitado, y el click no produce efecto.

`BrowserFactory` incluye las flags que evitan la mayor parte de eso, y `WebUI`
verifica el efecto de las acciones que pueden perderse. El detalle está comentado
en el código, en el punto exacto donde importa.

## Cómo agregar algo

Cada paquete tiene un patrón, y agregar una pieza es seguirlo:

| Querés agregar | Va en | Mirá como ejemplo |
|---|---|---|
| Una acción de UI nueva | `keywords/WebUI` | cualquier método existente |
| Un tipo de espera | `keywords/WaitUtils` | `visible`, `urlContiene` |
| Una fuente de datos | `helpers/` + `dataprovider/` | `ExcelHelper` + `DataProviderManager` |
| Un navegador | `enums/Browser` + `driver/BrowserFactory` | el caso `EDGE` |
| Un destino de reporte | `reports/` | `AllureManager` |
| Un proyecto nuevo | `projects/<nombre>/` + `objects/<nombre>.properties` | `projects/saucedemo` |

Lo que viene está en [ROADMAP.md](ROADMAP.md).

## Portfolio

Parte de una serie de repos de automatización:

| Repo | Stack | Estado |
|------|-------|--------|
| **selenium-automation-framework** (este) | Java · Selenium 4 · TestNG · Cucumber | ✅ |
| playwright-automation | TypeScript · Playwright | pendiente |
| cypress-automation | JavaScript · Cypress | pendiente |
