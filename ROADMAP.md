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

Cosas que están simplificadas a propósito y conviene tener a la vista:

- **`FailureHandling` está declarado pero no se usa.** El enum existe con la
  intención de que `WebUI` pueda continuar ante un fallo en vez de cortar, pero
  hoy todos los métodos cortan. Implementarlo requiere decidir dónde se acumulan
  los errores blandos.
- **El recurso a JavaScript en `WebUI` avisa pero no falla.** Es lo correcto hoy,
  pero si esos avisos se vuelven frecuentes hay que atacar el problema de entrega
  de eventos, no acostumbrarse a que el fallback lo tape.
- **`Platform` no se usa todavía.** Solo tiene sentido con Grid o Appium.
- **El proyecto de ejemplo es uno solo.** Un segundo proyecto con otra estructura
  probaría de verdad que el framework es genérico y no está moldeado a SauceDemo.
