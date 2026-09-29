# selenium-automation-framework

Framework de automatización sobre **Selenium 4**, con **TestNG** como runner
principal y **Cucumber** como camino opcional. Cubre interfaz, API, bases
relacionales y **Cassandra**, y permite cruzar las tres capas de una misma
aplicación en un solo test.

Todo lo que usa es open source: no hay licencias pagas ni por usuario.

Este documento describe **qué hace cada parte y cómo se usa**.

---

## Índice

| | |
|---|---|
| Arranque | [Correr las suites](#correr-las-suites) · [Configuración](#configuración) · [Driver](#driver) · [Clases base](#clases-base) |
| Interfaz | [WebUI](#webui) · [Esperas](#esperas) · [Alertas, frames, ventanas y tablas](#alertas-frames-ventanas-y-tablas) · [Locators](#locators) |
| Datos y servicios | [API](#api) · [Base de datos](#base-de-datos) · [Datos para los tests](#datos-para-los-tests) |
| Verificación | [Manejo de fallos](#manejo-de-fallos) · [Recurso a JavaScript](#recurso-a-javascript) · [Accesibilidad](#accesibilidad) |
| Salida | [Reportes y evidencia](#reportes-y-evidencia) · [Video](#video) · [Notificaciones por mail](#notificaciones-por-mail) |
| Ejecución | [Listeners](#listeners) · [Suites](#suites) · [Cucumber](#cucumber) · [Selenium Grid](#selenium-grid) · [Aplicación bajo prueba](#aplicación-bajo-prueba) · [GitLab CI](#gitlab-ci) |
| Referencia | [Estructura de carpetas](#estructura-de-carpetas) · [Todas las claves](#todas-las-claves-de-configuración) |

---

## Correr las suites

Requiere **JDK 21** y **Maven**. No hay drivers que descargar: Selenium Manager
los resuelve en runtime. Docker solo hace falta para el Grid y para la suite que
verifica el soporte de Cassandra.

```bash
mvn test
```

`mvn test` sin argumentos corre `regression.xml`. Para elegir otra:

```bash
mvn test -DsuiteXmlFile=src/test/resources/suites/unit.xml        # sin navegador ni red
mvn test -DsuiteXmlFile=src/test/resources/suites/smoke.xml       # camino crítico
mvn test -DsuiteXmlFile=src/test/resources/suites/cassandra.xml   # soporte de Cassandra (Docker)
mvn test -DsuiteXmlFile=src/test/resources/suites/grid.xml -Denv=grid
mvn test -Pcucumber                                               # los features
```

---

## Configuración

`ConfigManager` resuelve cada clave con esta precedencia:

```
variable de entorno  >  propiedad de sistema  >  archivo del perfil  >  error
```

La clave `page.load.timeout` se busca fuera del archivo como `PAGE_LOAD_TIMEOUT`:
punto a guion bajo, todo en mayúsculas. Vale para todas las claves sin excepción.

El perfil se elige con `-Denv=<perfil>` o `TEST_ENV=<perfil>`, y por defecto es
`local`. Cada perfil es un archivo en `src/test/resources/config/`:

| Perfil | Para qué |
|---|---|
| `local` | desarrollo, navegador visible |
| `ci` | headless, con recurso a JavaScript habilitado |
| `grid` | contra el Selenium Grid del docker-compose |
| `<nombre>-privado` | los datos de la aplicación bajo prueba; git lo ignora |

Los perfiles versionados no tienen URLs ni datos de ninguna aplicación. Esos van
en un perfil privado, que se arma copiando
`config/ejemplo-privado.properties.example`. Las credenciales, ni siquiera ahí:
solo por variable de entorno.

### Cómo se lee

```java
ConfigManager config = ConfigManager.get();

config.get("api.base.url");              // falla si no está, nombrando la clave
config.get("reporte.url", "");           // con valor por defecto
config.getInt("db.pool.size", 5);
config.getBool("headless");
```

Accesos con nombre para las claves frecuentes:

```java
config.baseUrl()      config.browser()       config.target()
config.headless()     config.gridUrl()       config.reintentos()
config.explicitTimeout()                     config.pageLoadTimeout()
config.fallbackJsHabilitado()                config.fallbackJsMaximo()
ConfigManager.perfilActivo()                 // nombre del perfil en uso
```

Una clave faltante o mal tipada lanza `ConfigKeyMissingException` o
`FrameworkException` nombrando la clave, en vez de propagarse como `null`.

---

## Driver

**`TargetFactory`** crea el `WebDriver` donde corresponda:

```java
WebDriver driver = TargetFactory.crear();                           // según el perfil
WebDriver driver = TargetFactory.crear(Target.GRID, Browser.CHROME, true);
```

`Target` es `LOCAL` o `GRID`. `Browser` es `CHROME`, `FIREFOX` o `EDGE`.

Al crear el driver aplica `page.load.timeout` del perfil y deja el **implicit
wait en cero**: toda la espera vive en `WaitUtils`.

**`BrowserFactory`** arma las capabilities de cada navegador:

```java
MutableCapabilities opciones = BrowserFactory.opciones(Browser.CHROME, true);
```

**`DriverManager`** guarda el driver del hilo actual, para que las suites
paralelas no se pisen:

```java
DriverManager.set(driver);
DriverManager.get();          // lanza si no hay driver en este hilo
DriverManager.hayDriver();
DriverManager.quit();
```

---

## Clases base

Un test extiende la base que corresponda al tipo de prueba:

| Clase | Navegador | Base de datos | HTTP |
|---|---|---|---|
| `BaseTest` | uno por método | — | — |
| `BaseApiTest` | — | — | sí |
| `BaseAppTest` | uno por método | sesión de Cassandra por suite | sí |

```java
public class LoginTest extends BaseTest {
    @Test
    public void usuarioValidoAccedeAlInventario() { ... }
}
```

`BaseTest` abre el navegador, navega a `base.url` y lo cierra al terminar el
método. Acepta `browser` y `target` como parámetros del XML de la suite:

```xml
<parameter name="browser" value="firefox"/>
```

`BaseAppTest` es la de los tests que cruzan capas: abre el navegador en
`base.url`, deja RestAssured limpio y comparte una sesión de Cassandra por suite.

---

## WebUI

La librería de acciones. Todos los métodos reciben `By`, nunca `WebElement`, y
esperan lo que corresponde antes de actuar.

### Navegación

```java
WebUI.abrirUrl(String url)      WebUI.refrescar()      WebUI.atras()
WebUI.adelante()                WebUI.titulo()         WebUI.urlActual()
```

### Click

```java
WebUI.click(By locator)
WebUI.clickPorJs(By locator)
WebUI.dobleClick(By locator)
WebUI.clickDerecho(By locator)
WebUI.hover(By locator)
WebUI.arrastrar(By origen, By destino)
```

`clickHasta` hace click y **verifica que el click haya tenido efecto**. Si el
efecto no llega, reintenta; si el disparador ya no está, entiende que el click
funcionó y espera. Antes de reintentar comprueba que el elemento siga presente,
así no repite una acción que ya ocurrió:

```java
WebUI.clickHasta(By locator, ExpectedCondition<?> efecto);
WebUI.clickHasta(By locator, ExpectedCondition<?> efecto, int segundosDeEfecto);
```

Condiciones listas para pasarle:

```java
WebUI.hastaQueAparezca(By locator)
WebUI.hastaQueLaUrlContenga(String fragmento)
WebUI.hastaQueLaUrlNoContenga(String fragmento)   // salir de una página
WebUI.hastaQueLaUrlCoincidaCon(String regex)      // cuando "contiene" ya se cumple
```

```java
WebUI.clickHasta(botonLogin, WebUI.hastaQueLaUrlNoContenga("/user/login"));
WebUI.clickHasta(botonCrear, WebUI.hastaQueLaUrlCoincidaCon(".*/issues/[0-9]+$"));
```

### Escritura y lectura

```java
WebUI.escribir(By locator, String texto)
WebUI.limpiarYEscribir(By locator, String texto)
WebUI.escribirVerificando(By locator, String texto)   // confirma el valor escrito
WebUI.presionarTecla(By locator, Keys tecla)
WebUI.subirArchivo(By locator, String rutaAbsoluta)

WebUI.obtenerTexto(By locator)
WebUI.obtenerAtributo(By locator, String atributo)
WebUI.obtenerValor(By locator)
WebUI.obtenerTextos(By locator)      // List<String> de todos los que matchean
```

### Estado

```java
WebUI.estaVisible(By locator)            WebUI.estaVisible(By locator, int segundos)
WebUI.estaHabilitado(By locator)         WebUI.estaSeleccionado(By locator)
WebUI.contar(By locator)                 // 0 si no hay ninguno, no lanza
```

### Checkboxes y selects

```java
WebUI.marcar(By locator)                 WebUI.desmarcar(By locator)
WebUI.seleccionarPorTexto(By locator, String texto)
WebUI.seleccionarPorValor(By locator, String valor)
WebUI.seleccionarPorIndice(By locator, int indice)
WebUI.opcionSeleccionada(By locator)     WebUI.todasLasOpciones(By locator)
```

### Scroll y pausa

```java
WebUI.scrollHasta(By locator)    WebUI.scrollAlFinal()    WebUI.scrollAlInicio()
WebUI.pausa(int milisegundos)    // deja un WARN en el log en cada uso
```

---

## Esperas

`WaitUtils` centraliza toda la espera explícita. El timeout por defecto sale de
`explicit.timeout`.

```java
WaitUtils.visible(By locator)                  WaitUtils.visible(By locator, int segundos)
WaitUtils.clickeable(By locator)               WaitUtils.clickeable(By locator, int segundos)
WaitUtils.presente(By locator)                 WaitUtils.todosVisibles(By locator)
WaitUtils.invisible(By locator)                WaitUtils.urlContiene(String fragmento)
WaitUtils.textoEs(By locator, String texto)    WaitUtils.alerta()

WaitUtils.hasta(ExpectedCondition<T> condicion, int segundos)    // lanza si no se cumple
WaitUtils.seCumple(ExpectedCondition<?> condicion, int segundos) // devuelve boolean
WaitUtils.estaVisible(By locator, int segundos)
```

---

## Alertas, frames, ventanas y tablas

```java
// AlertUtils
AlertUtils.aceptar()        AlertUtils.descartar()     AlertUtils.obtenerTexto()
AlertUtils.responder(String texto)                     AlertUtils.hayAlerta()
AlertUtils.aceptarSiHay()   // no lanza si no hay; lo usa el cierre del navegador

// FrameUtils
FrameUtils.entrar(int indice)       FrameUtils.entrar(String nombreOId)
FrameUtils.entrar(By locator)       FrameUtils.entrarAnidados(String... nombres)
FrameUtils.volverAlRaiz()           FrameUtils.subirUnNivel()
FrameUtils.textoDentroDe(String... nombres)   // entra, lee y vuelve al raíz

// WindowUtils
WindowUtils.handles()               WindowUtils.handleActual()
WindowUtils.cantidad()              WindowUtils.cambiarA(int indice)
WindowUtils.cambiarAlTitulo(String titulo)
WindowUtils.volverALaPrincipal()    WindowUtils.cerrarLasDemas()

// TableUtils — el primer parámetro es la clave del locator de la tabla
TableUtils.encabezados(String tabla)
TableUtils.cantidadDeFilas(String tabla)
TableUtils.celda(String tabla, int fila, int columna)
TableUtils.indiceDeColumna(String tabla, String encabezado)
TableUtils.columna(String tabla, String encabezado)
TableUtils.comoMapa(String tabla)                     // List<Map<String,String>>
TableUtils.buscarFila(String tabla, String encabezado, String texto)
```

---

## Locators

Los selectores viven fuera del código, en `src/test/resources/objects/*.properties`,
con formato `tipo:valor`:

```properties
login.usuario = css:[data-test='username']
login.boton   = id:login-button
producto.porNombre = xpath://div[text()='{0}']/ancestor::div[@class='item']
```

Se resuelven con `LocatorHelper`:

```java
LocatorHelper.by("login.usuario")
LocatorHelper.by("producto.porNombre", "Sauce Labs Backpack")   // reemplaza {0}, {1}...
```

Tipos admitidos: `id`, `name`, `css`, `xpath`, `class`, `tag`, `link`, `partialLink`.

Un locator inexistente, sin tipo o con un tipo desconocido falla nombrándolo y
explicando el formato correcto.

---

## Manejo de fallos

Los métodos de `WebUI` que pueden fallar tienen una sobrecarga que recibe una
política:

| `FailureHandling` | Qué hace al fallar |
|---|---|
| `STOP_ON_FAILURE` | relanza y corta el caso (comportamiento por defecto) |
| `CONTINUE_ON_FAILURE` | registra el fallo y sigue; el caso termina en rojo al final |
| `OPTIONAL` | lo deja en el log de debug y no cuenta como fallo |

```java
WebUI.click(banner, FailureHandling.OPTIONAL);
WebUI.escribir(campo, texto, FailureHandling.CONTINUE_ON_FAILURE);

// Para envolver cualquier acción propia
WebUI.intentar("cerrar el modal", () -> cerrarModal(), FailureHandling.OPTIONAL);
```

Sobrecargas disponibles: `click`, `escribir`, `limpiarYEscribir`,
`seleccionarPorTexto`, `marcar`, `desmarcar`, `hover`, `verificarAccesibilidad`.

`SoftFailures` acumula los fallos tolerados por hilo, y `SoftFailureListener` da
vuelta el resultado del caso a FAILURE si terminó con alguno:

```java
SoftFailures.hay()      SoftFailures.cantidad()    SoftFailures.registrados()
SoftFailures.resumen()  SoftFailures.limpiar()
```

---

## Recurso a JavaScript

Cuando un click o un `sendKeys` no produce efecto, `WebUI` puede rodearlo
invocando el DOM directamente. Se controla por perfil:

```properties
webui.fallback.js.enabled = true
webui.fallback.js.max     = 10
```

Cada uso se cuenta y queda registrado:

```java
FallbackTracker.cantidad()    FallbackTracker.usos()
FallbackTracker.resumen()     FallbackTracker.limpiar()
```

`FallbackGuardListener` publica el total en el encabezado del reporte, y
`FallbackGuardTest` corre como un caso más al final de la suite y **falla el
build** si el total supera `webui.fallback.js.max`.

---

## API

**`ApiClient`** tiene los verbos en tres variantes: anónima, autenticada con el
token del hilo, y con un token explícito.

```java
// Anónimas
ApiClient.get(String ruta)                  ApiClient.get(String ruta, Map<String,?> parametros)
ApiClient.post(String ruta, Object cuerpo)  ApiClient.put(String ruta, Object cuerpo)
ApiClient.patch(String ruta, Object cuerpo) ApiClient.delete(String ruta)

// Con el token del hilo (AuthManager)
ApiClient.getAuth(...)   ApiClient.postAuth(...)   ApiClient.putAuth(...)   ApiClient.deleteAuth(...)

// Con un token explícito
ApiClient.getConToken(String ruta, String token)
ApiClient.postConToken(String ruta, Object cuerpo, String token)
ApiClient.patchConToken(String ruta, Object cuerpo, String token)
ApiClient.deleteConToken(String ruta, String token)

// Specs de RestAssured, si hace falta bajar un nivel
ApiClient.anonimo()   ApiClient.autenticado()   ApiClient.conToken(String token)
ApiClient.autenticadoComo(String usuario, String password)
```

**`ApiResponse`** encadena aserciones cuyo mensaje de fallo incluye el cuerpo de
la respuesta:

```java
ApiClient.get("/products/1")
    .tieneCodigo(200)
    .tieneCampo("title")
    .campoEs("id", 1)
    .respondeEnMenosDe(2000)
    .cumpleElEsquema("producto.json");     // JSON Schema en resources/schemas/
```

Lectura:

```java
respuesta.codigo()      respuesta.cuerpo()      respuesta.tiempoMs()
respuesta.cabecera("X-Total-Count")             respuesta.campo("data.id")
respuesta.comoMapa()    respuesta.comoObjeto(Producto.class)
respuesta.comoListaDe("products", Producto.class)
respuesta.esExitosa()   respuesta.response()    // el Response crudo
```

**`AuthManager`** guarda el token JWT por hilo:

```java
AuthManager.token()        // login con api.usuario / api.password, y lo cachea
AuthManager.tokenDe(String usuario, String password)
AuthManager.hayToken()     AuthManager.limpiar()
```

**`ContractGuard`** compara la respuesta contra un contrato versionado en
`resources/contracts/` y devuelve solo los cambios que rompen: campos que
desaparecieron o que cambiaron de tipo.

```java
List<String> problemas = ContractGuard.cambiosRompientes("producto", respuesta);
```

**`Paginador`** recorre un endpoint paginado y devuelve todo junto, con tope de
50 páginas:

```java
List<Producto> todos = Paginador.todos("/products", "products", "total", 30, Producto.class);
```

**Filtros** que se aplican solos a toda petición:

- `ApiLogFilter` deja el intercambio HTTP en el reporte, enmascarando
  `authorization`, `cookie` y `x-api-key`, y truncando a 4000 caracteres.
- `RateLimitFilter` reintenta ante un 429 respetando `Retry-After`, con respaldo
  exponencial y un máximo de 3 reintentos.

---

## Base de datos

**`DatabaseManager`** administra el pool (HikariCP):

```java
DatabaseManager.conectar();                              // según el perfil
DatabaseManager.conectar(String url, String usuario, String password);
DatabaseManager.conexion()    DatabaseManager.hayConexion()    DatabaseManager.cerrar();
```

**`DatabaseHelper`** consulta y ejecuta con `PreparedStatement`:

```java
DatabaseHelper.consultar("SELECT * FROM cliente WHERE pais = ?", "AR");  // List<Map<String,String>>
DatabaseHelper.valorUnico("SELECT email FROM cliente WHERE id = ?", 7);  // Optional<String>
DatabaseHelper.contar("SELECT COUNT(*) FROM pedido WHERE estado = ?", "abierto");
DatabaseHelper.existe("SELECT 1 FROM cliente WHERE email = ?", email);
DatabaseHelper.ejecutar("UPDATE cliente SET activo = false WHERE id = ?", 7);
DatabaseHelper.ejecutarScript(SqlLoader.cargar("esquema"));
DatabaseHelper.enTransaccion(List.of(sql1, sql2));       // todo o nada
```

`consultar` devuelve la misma forma que `ExcelHelper.leerHoja`: una lista de
mapas columna → valor.

**`SqlLoader`** lee el SQL de `resources/sql/`, así no vive dentro del Java:

```java
SqlLoader.cargar("pedidos-abiertos")     // resources/sql/pedidos-abiertos.sql
SqlLoader.cargarCql("persona-por-cuit")  // resources/cql/persona-por-cuit.cql
SqlLoader.limpiarCache()
```

### Cassandra

Cassandra no habla JDBC, así que tiene su propia pieza: **`CassandraManager`**,
sobre el driver oficial de Apache. Una sesión por corrida, compartida entre
hilos, y siempre con parámetros enlazados:

```java
CassandraManager.conectar();                          // según el perfil
CassandraManager.consultar(String cql, Object... parametros)   // List<Map<String,Object>>
CassandraManager.primera(String cql, Object... parametros)     // Optional<Map<String,Object>>
CassandraManager.hayConexion()    CassandraManager.cerrar();
```

```java
Map<String, Object> fila = CassandraManager.primera(
        SqlLoader.cargarCql("persona-por-cuit"), cuit).orElseThrow();
```

Los valores conservan el tipo del motor: un `int` de Cassandra llega como
`Integer`, no como texto.

En CQL solo se filtra eficientemente por la clave de partición. Una consulta por
otra columna necesita `ALLOW FILTERING`, que en un cluster real lo recorre
entero: hay que consultar la tabla que está particionada por ese dato.

El timeout de las consultas es `cassandra.timeout`: 10 segundos por defecto, y no
los 2 del driver, que no alcanzan detrás de una VPN.

---

## Datos para los tests

### Excel y JSON

```java
ExcelHelper.leerHoja("data/usuarios.xlsx", "login")           // List<Map<String,String>>
ExcelHelper.comoDataProvider("data/usuarios.xlsx", "login")   // Object[][]
ExcelHelper.escribirHoja(ruta, hoja, filas)

JsonHelper.comoMapa("data/usuario.json")
JsonHelper.comoLista("data/usuarios.json")
JsonHelper.comoObjeto("data/usuario.json", Usuario.class)
JsonHelper.comoListaDe("data/usuarios.json", Usuario.class)
JsonHelper.escribir("data/salida.json", objeto)
JsonHelper.aTexto(objeto)
```

La primera fila de la hoja de Excel son los encabezados. Conserva los ceros a la
izquierda y no usa notación científica.

### DataProviders

`DataProviderManager` expone los datos con el formato que espera TestNG:

```java
@Test(dataProvider = "usuariosJson", dataProviderClass = DataProviderManager.class)
public void login(Map<String, String> usuario) { ... }
```

Disponibles: `usuariosJson`, `usuariosExcel`, `usuariosJsonParalelo`.

### Datos inventados

```java
FakeDataUtils.nombre()        FakeDataUtils.apellido()      FakeDataUtils.nombreCompleto()
FakeDataUtils.empresa()       FakeDataUtils.telefono()      FakeDataUtils.ciudad()
FakeDataUtils.direccion()     FakeDataUtils.codigoPostal()
FakeDataUtils.email()         // único por llamada
FakeDataUtils.password()      FakeDataUtils.texto(int palabras)
FakeDataUtils.numeroEntre(int desde, int hasta)
```

### Fechas

```java
DateUtils.hoy()        DateUtils.ahora()      DateUtils.timestampParaArchivo()
DateUtils.formatear(LocalDate fecha, String patron)
DateUtils.sumarDias(long dias, String patron)
DateUtils.restarDias(long dias, String patron)
```

---

## Accesibilidad

`WebUI.verificarAccesibilidad` inyecta **axe-core**, analiza la página y compara
contra una línea base versionada:

```java
WebUI.verificarAccesibilidad("login");
WebUI.verificarAccesibilidad("consulta", FailureHandling.CONTINUE_ON_FAILURE);
```

Falla —con `AccesibilidadException`— solo si **aparece una regla nueva** o si
**crece la cantidad de elementos** de una ya conocida. Todas las violaciones,
nuevas o viejas, quedan en el reporte como una tabla con la regla, el impacto,
cuántos elementos y el enlace a la documentación.

La línea base es un archivo por pantalla en `src/test/resources/a11y/`:

```properties
# src/test/resources/a11y/consulta.properties
select-name=1
```

Se regenera con:

```bash
mvn test -Da11y.actualizar=true
```

Si una regla mejora o desaparece, no falla: lo avisa en el log para que se pueda
bajar la línea base.

Las reglas evaluadas salen de `a11y.tags`, por defecto
`wcag2a,wcag2aa,wcag21a,wcag21aa`.

Acceso directo, sin línea base:

```java
List<ViolacionA11y> violaciones = AnalisisA11y.analizar();
List<ViolacionA11y> soloAhi     = AnalisisA11y.analizar(By.id("carrito"));
AnalisisA11y.reportar("login", violaciones);

// Cada violación: regla(), impacto(), ayuda(), ayudaUrl(), elementos(), cantidad(), gravedad()
```

Manejo de la línea base:

```java
LineaBaseA11y.existe("login")       LineaBaseA11y.leer("login")
LineaBaseA11y.guardar("login", violaciones)
LineaBaseA11y.regresiones("login", violaciones)    // lo que empeoró
LineaBaseA11y.enModoActualizacion()
```

---

## Reportes y evidencia

Cada corrida genera dos reportes:

| Reporte | Dónde | Cómo verlo |
|---|---|---|
| ExtentReports | `reports/ExtentReport.html` | se abre solo, con las imágenes embebidas |
| Allure | `allure-results/` | `mvn allure:serve` |

Los tests no escriben en el reporte: lo hace `TestListener`. Cuando un caso
falla, captura el screenshot **antes** de que se cierre el navegador y lo embebe
en el HTML, además de guardarlo en `evidence/`.

Un intento que falla y se reintenta (`retry.count`) deja la misma evidencia
—screenshot y video— pero figura como **advertencia**, no como fallo ni como
omitido: el resultado final lo decide el reintento, y la intermitencia queda a la
vista en vez de desaparecer detrás de un verde.

En el pipeline, el reporte y la evidencia quedan como artefactos del job, dentro
de GitLab. No se publican afuera: los screenshots muestran datos de la
aplicación.

Para escribir en el reporte desde un paso propio:

```java
ExtentTestManager.info("...")        ExtentTestManager.ok("...")
ExtentTestManager.advertencia("...") ExtentTestManager.fallo("...")
ExtentTestManager.omitido("...")     ExtentTestManager.falloConEvidencia("...")
ExtentTestManager.advertenciaConEvidencia("...")

AllureManager.adjuntarScreenshot(String nombre)
AllureManager.adjuntarTexto(String nombre, String contenido)
AllureManager.adjuntarHtml(String nombre, String html)
AllureManager.paso(String descripcion)    AllureManager.descripcion(String texto)
```

Capturas a mano:

```java
CaptureHelper.comoBytes()     CaptureHelper.comoBase64()
CaptureHelper.aArchivo(String nombre)    CaptureHelper.aArchivoSinFallar(String nombre)
```

### Metadatos del caso

`@FrameworkAnnotation` agrega autor, categoría y descripción al reporte:

```java
@Test(groups = "regresion", description = "Un usuario válido llega al listado")
@FrameworkAnnotation(autor = "Agustín", categoria = {"smoke", "login"})
public void usuarioValidoAccedeAlInventario() { ... }
```

### Información del entorno

```java
BrowserInfoUtils.navegador()          BrowserInfoUtils.version()
BrowserInfoUtils.sistemaOperativo()   BrowserInfoUtils.versionDeJava()
BrowserInfoUtils.resumen()
```

---

## Video

Graba lo que pasa en el navegador durante cada caso y guarda un MP4 en `videos/`.
Funciona local y en CI, headless o no, sin Grid y sin ffmpeg instalado.

```bash
mvn test                              # graba todo, guarda solo lo que falla
mvn test -DVIDEO_CUANDO=SIEMPRE       # guarda todos
mvn test -DVIDEO_CUANDO=NUNCA         # no graba
```

Un archivo por caso, con el nombre `Clase.metodo-<timestamp>.mp4`. La ruta del
video queda anotada en el reporte del caso.

**Cobertura:**

| | Local o CI sin Grid | Con Grid |
|---|---|---|
| Chrome · Edge | sí, por CDP | sí, contenedor de video |
| Firefox | no | sí, contenedor de video |

En Firefox no graba, lo dice en el log, y el caso corre igual.

Se arranca y se corta desde `TestListener`, así que no hay que hacer nada en los
tests. Uso directo, si hiciera falta:

```java
VideoRecorder.iniciar("MiTest.miCaso")
VideoRecorder.marcarParaConservar()
VideoRecorder.detener()        // Optional<Path>
VideoRecorder.grabando()       VideoRecorder.politica()
```

Ajustes por perfil o variable de entorno:

| Clave | Por defecto | Qué controla |
|---|---|---|
| `video.cuando` | `FALLOS` | `SIEMPRE`, `FALLOS` o `NUNCA` |
| `video.fps` | `4` | cuadros por segundo del MP4 |
| `video.ancho` | `640` | ancho máximo del cuadro |
| `video.alto` | `512` | alto máximo del cuadro |
| `video.calidad` | `60` | calidad JPEG de cada cuadro |
| `video.max.cuadros` | `900` | tope por caso |

---

## Notificaciones por mail

Al terminar la suite manda un resumen de la corrida. `ResumenDeCorrida` arma el
resumen y `EmailNotifier` lo envía; son piezas separadas.

**Se configura solo por variables de entorno**, nunca por archivo del perfil:

| Variable | Obligatoria | Por defecto |
|---|---|---|
| `MAIL_SMTP_HOST` | sí | — |
| `MAIL_DESTINATARIOS` | sí | — (separados por coma) |
| `MAIL_SMTP_PUERTO` | no | `587` |
| `MAIL_USUARIO` | no | sin usuario no hay autenticación ni TLS |
| `MAIL_PASSWORD` | no | — |
| `MAIL_REMITENTE` | no | `MAIL_USUARIO` |
| `MAIL_CUANDO` | no | `FALLOS` (`SIEMPRE`, `FALLOS`, `NUNCA`) |

Si falta `MAIL_SMTP_HOST` o `MAIL_DESTINATARIOS`, no hace nada y lo registra en
debug. Un error al enviar se registra y **nunca hace fallar la suite**.

```bash
export MAIL_SMTP_HOST=smtp.gmail.com MAIL_SMTP_PUERTO=587
export MAIL_USUARIO=... MAIL_PASSWORD=...       # contraseña de aplicación
export MAIL_DESTINATARIOS=equipo@ejemplo.com
mvn test
```

El listener está registrado en `regression.xml`. Para otras suites, agregar:

```xml
<listener class-name="com.silveira.listeners.NotificacionListener"/>
```

### La plantilla

El cuerpo sale de `src/main/resources/templates/mail-resumen.html`. Se reemplaza
sin tocar código:

```bash
export MAIL_PLANTILLA=/ruta/a/mi-plantilla.html
export MAIL_ASUNTO="{{estado}} · {{suite}} · {{fallados}} fallaron"
```

**Parámetros:** `suite`, `entorno`, `navegador`, `duracion`, `estado`, `color`,
`pasados`, `fallados`, `omitidos`, `total`, `urlReporte`.

**Secciones:**

| Sintaxis | Qué hace |
|---|---|
| `{{#fallos}}...{{/fallos}}` | repite el bloque por cada caso fallado, con `clase`, `metodo`, `nombreCompleto` y `mensaje` |
| `{{#hayFallos}}...{{/hayFallos}}` | incluye el bloque solo si algo falló |
| `{{^hayFallos}}...{{/hayFallos}}` | lo incluye solo si no falló nada |
| `{{#urlReporte}}...{{/urlReporte}}` | solo si hay `REPORTE_URL` configurada |

```html
<h2 style="color:{{color}}">{{suite}} en {{entorno}} — {{duracion}}</h2>
{{#hayFallos}}
  <ul>{{#fallos}}<li>{{clase}}.{{metodo}}: {{mensaje}}</li>{{/fallos}}</ul>
{{/hayFallos}}
```

Los valores se escapan como HTML al sustituirlos. Un parámetro mal escrito queda
visible en el mail en vez de desaparecer.

`REPORTE_URL` es el enlace al reporte que va en el cuerpo. Si no está, el bloque
del enlace no se incluye.

---

## Listeners

Se declaran en el `<listeners>` de cada XML de suite.

| Listener | Cuándo actúa | Qué hace |
|---|---|---|
| `TestListener` | por caso | crea la entrada del reporte, captura evidencia al fallar, arranca y corta el video |
| `AnnotationTransformer` | al armar la suite | aplica `RetryAnalyzer` a todos los casos |
| `RetryAnalyzer` | al fallar un caso | lo reintenta hasta `retry.count` veces |
| `SoftFailureListener` | después de cada caso | pasa el caso a FAILURE si quedaron fallos tolerados |
| `FallbackGuardListener` | al terminar la suite | publica el uso de JavaScript en el reporte |
| `NotificacionListener` | al terminar la suite | arma el resumen, lo loguea y lo manda por mail |

---

## Suites

Los XML están en `src/test/resources/suites/`.

| Suite | Contenido | Requiere |
|---|---|---|
| `unit` | lógica pura del framework | nada |
| `smoke` | camino crítico | navegador |
| `regression` | unitarios + aplicación + guardias | navegador, perfil privado |
| `grid` | camino crítico contra el Grid | Docker |
| `cassandra` | `CassandraManager` contra un Cassandra real | Docker |
| `cucumber` | los features | navegador |

Los tests de la aplicación viven en `com.silveira.projects.consulta.tests`, y
`smoke`, `regression` y `grid` los toman de ahí.

---

## Cucumber

Camino opcional sobre las mismas páginas y el mismo `WebUI`:

```bash
mvn test -Pcucumber
```

- Features en `src/test/resources/features/`
- Steps en `src/test/java/com/silveira/cucumber/steps/`
- Hooks en `cucumber/hooks/CucumberHooks` — abre y cierra el navegador
- Runner en `cucumber/runners/CucumberRunner`

---

## Selenium Grid

```bash
docker compose -f docker-compose.grid.yml up -d
mvn test -DsuiteXmlFile=src/test/resources/suites/grid.xml -Denv=grid
docker compose -f docker-compose.grid.yml down
```

Levanta un hub y nodos de Chrome y Firefox, con versiones fijadas. Un contenedor
aparte graba en video la sesión del nodo de Chrome, y los archivos quedan en
`grid-videos/`.

El perfil `grid` usa `target=GRID` y `grid.url=http://localhost:4444`. El mismo
test corre sin cambios local o contra el Grid.

---

## Aplicación bajo prueba

Los datos que identifican a la aplicación —URLs, keyspace, datacenter— van en un
perfil privado que git ignora:

```bash
cp src/test/resources/config/ejemplo-privado.properties.example \
   src/test/resources/config/demo-privado.properties
mvn test -Denv=demo-privado
```

Credenciales y datos personales, solo por variable de entorno: `APP_USUARIO`,
`APP_PASSWORD`, `API_USUARIO`, `API_PASSWORD`, `CASSANDRA_USUARIO`,
`CASSANDRA_PASSWORD` y `CUIT_PRUEBA`.

Los tests extienden `BaseAppTest` y verifican un mismo dato por las tres capas:
lo que muestra la interfaz, lo que responde el servicio y lo que guarda
Cassandra.

---

## GitLab CI

`.gitlab-ci.yml` tiene dos etapas:

| Job | Qué corre | Cuándo |
|---|---|---|
| `unitarios` | `unit.xml`, sin navegador ni red | en todo push |
| `regresion` | `regression.xml` contra la aplicación | en merge requests, en la rama principal y a mano |

Supone un runner con executor Docker. Chrome corre como servicio del job
(`selenium/standalone-chrome`, misma versión que el Grid) y el framework se conecta a él como a un Grid,
así que el runner no necesita tener nada instalado.

Los datos de la aplicación llegan como variables de CI/CD enmascaradas; la
lista está al principio del archivo. Los resultados aparecen en la pestaña de
tests del pipeline y del merge request, y el reporte y la evidencia quedan como
artefactos del job por 30 días.

---

## Estructura de carpetas

```
src/main/java/com/silveira/          EL FRAMEWORK
├── config/          ConfigManager · FrameworkConstants
├── driver/          DriverManager · BrowserFactory · TargetFactory
├── enums/           Browser · Target · Platform · FailureHandling
├── exceptions/      FrameworkException y 5 derivadas
├── keywords/        WebUI · WaitUtils · AlertUtils · FrameUtils · WindowUtils
│                    TableUtils · SoftFailures · FallbackTracker
├── api/             ApiClient · ApiResponse · AuthManager · ContractGuard
│                    Paginador · ApiLogFilter · RateLimitFilter
├── db/              DatabaseManager · DatabaseHelper · SqlLoader
├── a11y/            AnalisisA11y · LineaBaseA11y · ViolacionA11y
├── video/           VideoRecorder · ConexionCdp · CodificadorMp4
├── notifications/   ResumenDeCorrida · EmailNotifier · Plantilla
├── helpers/         Properties · Locator · Json · Excel · File · Capture
├── utils/           Log · Date · FakeData · BrowserInfo
├── reports/         ExtentReportManager · ExtentTestManager · AllureManager
└── annotations/     FrameworkAnnotation

src/main/resources/
├── log4j2.xml
└── templates/       mail-resumen.html

src/test/java/com/silveira/          QUIEN LO USA
├── common/          BaseTest · BaseApiTest · BaseAppTest
├── listeners/       los seis listeners
├── dataprovider/    DataProviderManager
├── guards/          FallbackGuardTest
├── projects/        consulta: la aplicación bajo prueba
└── cucumber/        runner, steps y hooks

src/test/resources/
├── config/          un .properties por ambiente
├── suites/          los XML de cada suite
├── objects/         locators externalizados
├── schemas/         JSON Schema de las respuestas
├── contracts/       contratos versionados de los endpoints
├── a11y/            línea base de accesibilidad por pantalla
├── sql/ · cql/      consultas fuera del Java, SQL y Cassandra
├── data/            JSON y Excel para los DataProviders
└── features/        los .feature de Cucumber

Salidas (todas ignoradas por git)
reports/  evidence/  videos/  grid-videos/  logs/  allure-results/
```

---

## Todas las claves de configuración

### Del perfil, variable de entorno o `-D`

| Clave | Ejemplo | Qué controla |
|---|---|---|
| `base.url` | `https://app.ejemplo` | URL que abre `BaseTest` |
| `browser` | `chrome` | `chrome`, `firefox` o `edge` |
| `headless` | `true` | navegador sin ventana |
| `target` | `LOCAL` | `LOCAL` o `GRID` |
| `grid.url` | `http://localhost:4444` | hub del Grid |
| `page.load.timeout` | `30` | segundos para cargar una página |
| `explicit.timeout` | `15` | segundos por defecto de `WaitUtils` |
| `retry.count` | `1` | reintentos de un caso fallado |
| `webui.fallback.js.enabled` | `true` | permitir el recurso a JavaScript |
| `webui.fallback.js.max` | `10` | tope antes de romper el build |
| `api.base.url` | `https://servicios.ejemplo` | base de las peticiones |
| `api.timeout` | `30` | segundos de la petición |
| `api.usuario` · `api.password` | | credenciales de `AuthManager` |
| `db.url` · `db.user` · `db.password` | | conexión JDBC |
| `db.pool.size` | `5` | conexiones del pool |
| `db.timeout` | `15` | segundos de la consulta |
| `cassandra.contact.points` | `host1:9042,host2` | nodos del cluster; el puerto es opcional |
| `cassandra.datacenter` | `datacenter1` | datacenter local; tiene que coincidir con el del cluster |
| `cassandra.keyspace` | | keyspace por defecto de la sesión |
| `cassandra.usuario` · `cassandra.password` | | solo si el cluster pide credenciales |
| `cassandra.timeout` | `10` | segundos de cada consulta |
| `a11y.tags` | `wcag2a,wcag2aa` | reglas de axe a evaluar |
| `video.cuando` | `FALLOS` | ver [Video](#video) |
| `video.fps` · `video.ancho` · `video.alto` · `video.calidad` · `video.max.cuadros` | | ver [Video](#video) |
| `reporte.url` | | enlace al reporte que va en el mail |
| `mail.plantilla` | | ruta a una plantilla propia |
| `mail.asunto` | | plantilla del asunto |

### Solo por variable de entorno

`MAIL_SMTP_HOST` · `MAIL_SMTP_PUERTO` · `MAIL_USUARIO` · `MAIL_PASSWORD` ·
`MAIL_REMITENTE` · `MAIL_DESTINATARIOS` · `MAIL_CUANDO`

### Solo por propiedad de sistema

| Propiedad | Qué hace |
|---|---|
| `-Denv=<perfil>` | elige el perfil de configuración |
| `-DsuiteXmlFile=<ruta>` | elige la suite |
| `-Da11y.actualizar=true` | regenera las líneas base de accesibilidad |
