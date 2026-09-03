# ROADMAP

Lo que el framework todavía no hace, con dónde se enchufa cada cosa. El orden es
por relación entre valor y esfuerzo, no por preferencia.

Nada de esto es un pendiente urgente: la v1 funciona completa sin ellos. Es el
mapa de por dónde crece.

---

## Testing de API

**Paquete:** `api/` nuevo · **Toca:** `pom.xml`, `config/`

RestAssured para pegarle a endpoints. El valor real no es tener tests de API
sueltos, sino usarlos como **precondición de los de UI**: crear un usuario por
API y después probar el login por interfaz es más rápido y menos frágil que
armarlo a mano en cada caso.

- `api/ApiClient` con la URL base desde `ConfigManager`
- `api/RequestBuilder` para autenticación y headers comunes
- Un `BaseApiTest` paralelo a `BaseTest`, sin navegador

## Base de datos

**Paquete:** `helpers/DatabaseHelper` · **Toca:** `pom.xml`, `config/`

Consultas JDBC para verificar en la base lo que la interfaz dice que pasó. Un
checkout que muestra "gracias por tu compra" pero no dejó la orden en la base es
un test que pasa y un bug que se escapa.

- Conexión desde `ConfigManager` (`db.url`, `db.user`, `db.password`)
- `consultar(sql)` devolviendo `List<Map<String,String>>`, igual que `ExcelHelper`
- Las credenciales por variable de entorno, nunca versionadas

## Selenium Grid en Docker

**Toca:** `docker-compose.yml` nuevo, `driver/TargetFactory` (ya está listo)

`TargetFactory` ya soporta `Target.GRID`, pero no hay Grid contra el cual correr.
Falta el `docker-compose` con hub y nodos, y un job de CI que lo levante.

Es lo que convierte el `target=GRID` de una capacidad declarada en una demostrable.

## Notificaciones

**Paquete:** `notifications/` nuevo · **Toca:** `listeners/TestListener`

Resumen de la corrida a Telegram o mail cuando termina una suite. El listener ya
tiene el `onFinish` donde engancharlo.

- `notifications/TelegramNotifier` con token y chat por variable de entorno
- `notifications/EmailNotifier` con el reporte de Extent adjunto
- Enviar solo si hubo fallos, configurable: una notificación que llega siempre se
  vuelve ruido y se ignora

## Grabación de pantalla

**Paquete:** `helpers/ScreenRecorderHelper` · **Toca:** `listeners/TestListener`

Video del caso que falla, además del screenshot. Para fallos de timing —donde la
foto del final no dice qué pasó— es la diferencia entre diagnosticar y adivinar.

Ojo con el costo: grabar todo llena el disco rápido. Debería activarse por
configuración y solo conservar los videos de los casos fallidos.

## Visual regression

**Paquete:** `visual/` nuevo

Comparación de screenshots contra una línea base para detectar cambios visuales
que ningún assert funcional captura. Necesita una estrategia de tolerancia y de
actualización de la base, que es la parte difícil, no la comparación.

## Accesibilidad

**Paquete:** `a11y/` nuevo

Inyectar axe-core y reportar violaciones WCAG. Se integra bien como un método más
de `WebUI` (`WebUI.verificarAccesibilidad()`) para poder sumarlo a casos que ya
existen sin escribir casos nuevos.

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

- **`clickHasta` reintenta hasta 3 veces.** Ahora verifica que el disparador siga
  presente antes de reintentar, así que no repite una acción que ya ocurrió. Pero
  si un botón sigue visible después de un click que sí tuvo efecto parcial, el
  reintento es posible. Para acciones no idempotentes conviene pasar un efecto
  que sea inequívoco, como un cambio de URL.

---

## Resuelto

Lo que estaba en esta sección y se cerró, con lo que se aprendió en el camino.

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
