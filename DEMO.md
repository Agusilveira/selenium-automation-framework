# Demo: el framework contra una aplicación real

Plan y estado de la rama `demo-base`. Está escrito para retomarlo desde otra
máquina —la que tiene acceso a la aplicación— sin depender de ninguna
conversación previa.

**Objetivo:** mostrar el framework corriendo contra una aplicación interna, con
una prueba que busca un mismo dato por CUIT en las tres capas —interfaz,
servicio y Cassandra— y verifica que coincidan. Después de la corrida, recorrer
los resultados y cómo está armado.

---

## Reglas que no se negocian

Esta rama vive en un repositorio **público**. Nada que identifique a la
aplicación puede llegar ahí:

- **URLs, keyspace, datacenter, rutas de endpoints:** en
  `config/<nombre>-privado.properties`, que git ignora.
- **Usuarios, contraseñas, tokens y el CUIT de prueba:** solo por variable de
  entorno. Ni en el perfil privado.
- **La collection de Postman:** se lee, no se copia al repositorio.
- **El proyecto de la aplicación (`projects/consulta`):** no se sube al repo
  personal. Selectores, nombres de campos y de tablas describen la aplicación.
  Va a un repositorio de la empresa, o queda local.
- **Reportes, screenshots y videos:** muestran datos reales. Se miran en la
  demo, no se publican.

---

## Lo que ya está hecho

- **Limpieza:** sin los proyectos de ejemplo. Queda el núcleo del framework con
  sus tests unitarios (`unit.xml`, 54 casos).
- **Perfiles sin datos de ninguna aplicación**, y la plantilla
  `config/ejemplo-privado.properties.example`.
- **`CassandraManager`:** sesión, consultas con parámetros enlazados y timeouts
  pensados para una VPN. Verificado contra un Cassandra 5.0 real con
  `cassandra.xml`.
- **`BaseAppTest`:** navegador por método, sesión de Cassandra por suite y
  RestAssured limpio. Es la base del test del cruce.
- **`SqlLoader.cargarCql`:** las consultas CQL van en `resources/cql/`.
- **`.gitlab-ci.yml`:** unitarios en todo push; regresión con Chrome como
  servicio del job.
- **Suites:** `smoke`, `regression` y `grid` apuntan a
  `com.silveira.projects.consulta.tests`, que todavía no existe.

---

## Lo que falta, en orden

1. **Que compile en la máquina del trabajo.** `mvn test -DsuiteXmlFile=src/test/resources/suites/unit.xml`.
   Si Maven no llega a Maven Central, la red de la empresa probablemente tenga un
   espejo (Nexus, Artifactory): se configura en `~/.m2/settings.xml`, no en el
   `pom`.

2. **Perfil privado y variables.** Copiar la plantilla a
   `config/demo-privado.properties` y completar URLs, contact points, datacenter y
   keyspace. Exportar `APP_USUARIO`, `APP_PASSWORD`, `API_USUARIO`,
   `API_PASSWORD`, `CUIT_PRUEBA` y, si el cluster lo pide, `CASSANDRA_USUARIO` y
   `CASSANDRA_PASSWORD`. Correr todo con `-Denv=demo-privado`.

3. **El token de los servicios.** `AuthManager` está hecho a la medida de un
   login JSON en `/auth/login` que devuelve `accessToken`. Con la collection a la
   vista, generalizarlo: ruta, forma del cuerpo (JSON o formulario, como en
   OAuth), y dónde viene el token en la respuesta. Lo que cambie por aplicación
   va a claves de configuración, no al código.

4. **Cassandra.** Probar la conexión primero, sola. Si falla sin decir por qué,
   lo más común es un datacenter que no coincide con el del cluster. Si el
   cluster usa SSL, `CassandraManager` todavía no lo configura.
   Averiguar **cuál es la clave de partición** de la tabla que se consulta: si no
   es el CUIT, buscar la tabla que sí esté particionada por CUIT antes de usar
   `ALLOW FILTERING`. La consulta va en `resources/cql/persona-por-cuit.cql`.

5. **El servicio.** Un cliente en `projects/consulta/api/` sobre `ApiClient`,
   con el token del paso 3. La ruta del endpoint, al perfil privado.

6. **La interfaz.** Page objects en `projects/consulta/pages/`: login y
   búsqueda por CUIT. Selectores en `objects/consulta.properties`, con
   `LocatorHelper`. Usar `clickHasta` con un efecto concreto en los botones que
   disparan navegación o búsqueda.

7. **El cruce.** `projects/consulta/tests/CruceCuitTest extends BaseAppTest`:
   buscar `CUIT_PRUEBA` en la interfaz, pedirlo al servicio, leerlo de Cassandra,
   y afirmar que dos o tres campos significativos coinciden en las tres capas.
   Grupos `smoke` y `regresion`. Si un campo tiene formatos distintos por capa
   —fechas, montos—, normalizar en el test y decir por qué en un comentario.

8. **Accesibilidad.** `WebUI.verificarAccesibilidad("consulta")` en la pantalla
   de resultados, y generar la línea base con `-Da11y.actualizar=true`. Revisar el
   archivo antes de guardarlo.

9. **Ensayo.** Correr `regression.xml` completo dos veces seguidas: tiene que
   dar lo mismo. Preparar un fallo provocado —por ejemplo, un valor esperado
   cambiado— para mostrar el screenshot, el video y el mensaje.

10. **Opcional:** el mismo cruce en Gherkin en `features/`, para mostrar que un
    perfil no programador puede leer y escribir escenarios.

---

## Guion de la demo

1. **Qué problema resuelve (1 min).** Probar a mano que un dato es igual en la
   pantalla, en el servicio y en la base es lento y nadie lo repite en cada
   versión.
2. **La corrida (3 min).** Lanzarla en vivo, con el navegador visible.
3. **Los resultados (3 min).** El reporte con el cruce en verde, la tabla de
   accesibilidad, el log de las llamadas HTTP con las cabeceras sensibles
   enmascaradas. Después, el fallo provocado: screenshot, video y el mensaje que
   dice qué capa no coincidió.
4. **Cómo está armado (4 min).** Un test se lee como el flujo que prueba;
   esperas, evidencia y reporte los pone el framework. Selectores y consultas
   fuera del código. Configuración por perfil y secretos solo por entorno.
5. **Cierre (2 min).** A qué equipos les sirve, qué hace falta para usarlo, todo
   open source, curva de aprendizaje, y cómo se despliega en GitLab CI. El mismo
   enfoque se puede aplicar con Playwright o Cypress.
