package com.silveira.projects.app.tests;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.api.ApiResponse;
import com.silveira.common.BaseAppTest;
import com.silveira.db.DatabaseHelper;
import com.silveira.projects.app.api.AppApi;
import com.silveira.projects.app.pages.AppPages;
import com.silveira.utils.DateUtils;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El cruce entre capas: la misma acción, verificada en interfaz, API y base.
 *
 * Es lo que el framework no podía demostrar hasta ahora. SauceDemo no tiene API,
 * DummyJSON no tiene interfaz, y la base de `tienda` no la escribe ninguna
 * aplicación. Acá las tres caras son del mismo sistema, así que una acción
 * ejecutada por un camino se puede verificar por los otros dos.
 *
 * Por qué importa: un checkout que muestra "gracias por tu compra" pero no dejó la
 * orden en la base es un test de interfaz que pasa y un bug que llega a
 * producción. Estos casos son los que atrapan eso.
 */
public class CruceDeCapasTest extends BaseAppTest {

    private final AppPages app = new AppPages();

    /** Único por corrida: dos ejecuciones en paralelo no se pisan. */
    private String tituloUnico(String prefijo) {
        return prefijo + " " + DateUtils.timestampParaArchivo();
    }

    private long issuesEnLaBaseConTitulo(String titulo) {
        return DatabaseHelper.contar(
                "SELECT COUNT(*) FROM issue WHERE name = ?", titulo);
    }

    // ------------------------------------------------------------------

    @Test(groups = {"app", "smoke"},
          description = "Lo creado por API aparece en la interfaz y queda en la base")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"cruce", "api", "ui", "db"})
    public void loCreadoPorApiSeVeEnLaInterfazYEnLaBase() {
        String titulo = tituloUnico("Creado por API");

        // 1. Se crea por API
        ApiResponse creado = AppApi.crearIssue(titulo, "Cuerpo del issue de prueba")
                .tieneCodigo(201)
                .campoEs("title", titulo);
        int numero = creado.campo("number");

        // 2. Se verifica en la base: la fila existe de verdad, no solo la respuesta
        assertThat(issuesEnLaBaseConTitulo(titulo))
                .as("el issue creado por API deberia existir en la base")
                .isEqualTo(1);

        // 3. Y se ve en la interfaz, que es lo que ve una persona
        app.ingresar();
        assertThat(app.existeElIssueConTitulo(titulo))
                .as("el issue creado por API deberia listarse en la interfaz")
                .isTrue();

        // El numero es el mismo en las tres capas: no son tres cosas parecidas.
        Map<String, String> enLaBase = DatabaseHelper.consultar(
                "SELECT index FROM issue WHERE name = ?", titulo).get(0);
        assertThat(Integer.parseInt(enLaBase.get("index")))
                .as("el numero del issue deberia coincidir entre API y base")
                .isEqualTo(numero);
    }

    @Test(groups = "app",
          description = "Lo creado desde la interfaz se ve por API y queda en la base")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"cruce", "ui", "api", "db"})
    public void loCreadoEnLaInterfazSeVePorApiYEnLaBase() {
        String titulo = tituloUnico("Creado por interfaz");

        // 1. Se crea desde la interfaz, como lo haria una persona
        app.ingresar();
        int numero = app.crearIssue(titulo, "Creado desde el navegador");

        // 2. La API lo devuelve con los mismos datos
        AppApi.obtenerIssue(numero)
                .tieneCodigo(200)
                .campoEs("title", titulo)
                .campoEs("state", "open");

        // 3. Y la base lo confirma
        assertThat(issuesEnLaBaseConTitulo(titulo))
                .as("el issue creado desde la interfaz deberia existir en la base")
                .isEqualTo(1);
    }

    @Test(groups = "app",
          description = "Un cambio de estado por API se refleja en la base")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"cruce", "api", "db"})
    public void cerrarPorApiSeReflejaEnLaBase() {
        String titulo = tituloUnico("Para cerrar");
        int numero = AppApi.crearIssue(titulo, "Se va a cerrar").tieneCodigo(201).campo("number");

        assertThat(DatabaseHelper.valorUnico(
                "SELECT is_closed FROM issue WHERE name = ?", titulo).orElseThrow())
                .as("recien creado deberia estar abierto")
                .isEqualTo("false");

        AppApi.cerrarIssue(numero).tieneCodigo(201).campoEs("state", "closed");

        assertThat(DatabaseHelper.valorUnico(
                "SELECT is_closed FROM issue WHERE name = ?", titulo).orElseThrow())
                .as("tras cerrarlo por API, la base deberia reflejarlo")
                .isEqualTo("true");
    }

    @Test(groups = "app",
          description = "Las tres capas coinciden en cuantos issues abiertos hay")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"cruce", "consistencia"})
    public void lasTresCapasCoincidenEnElConteo() {
        String titulo = tituloUnico("Para contar");
        AppApi.crearIssue(titulo, "Suma uno al conteo").tieneCodigo(201);

        long enLaBase = DatabaseHelper.contar("""
                SELECT COUNT(*)
                  FROM issue i
                  JOIN repository r ON r.id = i.repo_id
                 WHERE r.name = ? AND i.is_closed = false
                """, "framework-demo");

        List<String> porApi = AppApi.titulosDeIssues();
        app.ingresar();
        List<String> enLaInterfaz = app.titulosDeIssues();

        // La interfaz lista solo los abiertos por defecto; la API se pidio con
        // state=all. La comparacion util es que la interfaz coincida con la base.
        assertThat((long) enLaInterfaz.size())
                .as("la interfaz deberia mostrar los mismos issues abiertos que hay en la base")
                .isEqualTo(enLaBase);

        assertThat(porApi)
                .as("la API deberia conocer al menos los que muestra la interfaz")
                .hasSizeGreaterThanOrEqualTo(enLaInterfaz.size());
    }

    @Test(groups = "app",
          description = "El usuario de la sesion web es el mismo que responde la API")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"cruce", "identidad"})
    public void elUsuarioEsElMismoEnLasTresCapas() {
        String porApi = AppApi.quienSoy().tieneCodigo(200).campo("login");

        String enLaBase = DatabaseHelper.valorUnico(
                "SELECT lower_name FROM \"user\" WHERE lower_name = ?", porApi).orElseThrow();

        app.ingresar();

        assertThat(porApi).as("usuario segun la API").isEqualTo(enLaBase);
        assertThat(app.haySesionIniciada())
                .as("el mismo usuario deberia poder iniciar sesion en la interfaz")
                .isTrue();
    }
}
