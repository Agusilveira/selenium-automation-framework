# ROADMAP

Lo que el framework todavía no hace, con dónde se enchufa cada cosa. El orden es
por relación entre valor y esfuerzo, no por preferencia.

Nada de esto es un pendiente urgente: la v1 funciona completa sin ellos. Es el
mapa de por dónde crece.

---

## Notificaciones

**Paquete:** `notifications/` nuevo · **Toca:** `listeners/TestListener`

Resumen de la corrida a Telegram o mail cuando termina una suite. El listener ya
tiene el `onFinish` donde engancharlo.

- `notifications/TelegramNotifier` con token y chat por variable de entorno
- `notifications/EmailNotifier` con el reporte de Extent adjunto
- Enviar solo si hubo fallos, configurable: una notificación que llega siempre se
  vuelve ruido y se ignora

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

- **El cruce completo entre capas no está demostrado.** "Compro por UI, verifico
  por API, confirmo en la base" necesita una app cuyas tres caras sean nuestras:
  SauceDemo no tiene API, DummyJSON no tiene UI, y la base de `tienda` no la
  escribe ninguna aplicación. Las tres capas existen y funcionan por separado, y
  los fixtures son el puente. El cruce se puede demostrar cuando se resuelva el
  ítem de Selenium Grid en Docker, que trae la infraestructura necesaria.

- **El recurso a JavaScript cuesta 12 segundos por click en el Grid.** Sobre el
  Grid, los botones del checkout de SauceDemo necesitan JavaScript el 100% de las
  veces, no intermitentemente. El framework igual hace 3 intentos de 4 segundos
  antes de recurrir a él, así que `compraCompleta` pasa de 15 a 27 segundos. Una
  memoria por corrida —"este locator ya necesitó JavaScript, no vuelvas a esperar
  12 segundos"— lo resolvería, a costa de sumarle estado a `clickHasta`.

- **`clickHasta` reintenta hasta 3 veces.** Ahora verifica que el disparador siga
  presente antes de reintentar, así que no repite una acción que ya ocurrió. Pero
  si un botón sigue visible después de un click que sí tuvo efecto parcial, el
  reintento es posible. Para acciones no idempotentes conviene pasar un efecto
  que sea inequívoco, como un cambio de URL.

---

## Resuelto

Lo que estaba en esta sección y se cerró, con lo que se aprendió en el camino.

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
