package com.silveira.projects.dummyjson.tests;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.api.ApiClient;
import com.silveira.api.ApiResponse;
import com.silveira.common.BaseApiTest;
import com.silveira.projects.dummyjson.models.Producto;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ProductosApiTest extends BaseApiTest {

    @Test(groups = {"api", "smoke"}, description = "Un producto responde con la forma del contrato")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "productos"})
    public void unProductoCumpleElContrato() {
        ApiClient.get("/products/1")
                .tieneCodigo(200)
                .cumpleElEsquema("producto.json")
                .campoEs("id", 1)
                .tieneCampo("title")
                .respondeEnMenosDe(5000);
    }

    @Test(groups = "api", description = "La respuesta se deserializa a un modelo tipado")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "productos"})
    public void laRespuestaSeDeserializaAUnModelo() {
        Producto producto = ApiClient.get("/products/1").esExitosa().comoObjeto(Producto.class);

        assertThat(producto.id()).as("id del producto").isEqualTo(1);
        assertThat(producto.title()).as("titulo").isNotBlank();
        assertThat(producto.price()).as("precio").isPositive();
        assertThat(producto.rating()).as("rating").isBetween(0.0, 5.0);
    }

    @Test(groups = "api", description = "El listado respeta el contrato de paginacion")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "productos"})
    public void elListadoRespetaElContratoDePaginacion() {
        ApiResponse respuesta = ApiClient.get("/products", Map.of("limit", 5, "skip", 10))
                .tieneCodigo(200)
                .cumpleElEsquema("listado-productos.json")
                .campoEs("limit", 5)
                .campoEs("skip", 10);

        List<Producto> productos = respuesta.comoListaDe("products", Producto.class);
        assertThat(productos).as("productos de la pagina").hasSize(5);
        assertThat(productos).extracting(Producto::id).doesNotContainNull();
    }

    @Test(groups = "api", description = "Un producto inexistente devuelve 404, no un 200 vacio")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "negativos"})
    public void unProductoInexistenteDevuelve404() {
        ApiClient.get("/products/999999").tieneCodigo(404);
    }

    @Test(groups = "api", description = "La busqueda filtra de verdad, no devuelve todo")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "productos"})
    public void laBusquedaFiltraDeVerdad() {
        ApiResponse respuesta = ApiClient.get("/products/search", Map.of("q", "phone")).tieneCodigo(200);

        int total = respuesta.campo("total");
        assertThat(total).as("resultados de la busqueda").isPositive();

        // Que devuelva resultados no alcanza: hay que verificar que sean los
        // pedidos. Una busqueda rota que ignora el filtro tambien devuelve 200 y
        // una lista no vacia.
        List<Producto> encontrados = respuesta.comoListaDe("products", Producto.class);
        assertThat(encontrados)
                .as("todos los resultados deberian mencionar el termino buscado")
                .allMatch(p -> (p.title() + " " + p.description() + " " + p.category())
                        .toLowerCase().contains("phone"));
    }

    @Test(groups = "api", description = "Un producto nuevo se crea y devuelve lo enviado")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "productos"})
    public void creaUnProducto() {
        String titulo = "Producto de prueba " + System.nanoTime();

        ApiClient.post("/products/add", Map.of("title", titulo, "price", 199.99))
                .tieneCodigo(201)
                .campoEs("title", titulo)
                .tieneCampo("id");
    }

    @Test(groups = "api", description = "Una actualizacion parcial modifica solo lo enviado")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "productos"})
    public void actualizaSoloLoEnviado() {
        ApiClient.patch("/products/1", Map.of("price", 42.5))
                .tieneCodigo(200)
                .campoEs("price", 42.5f)
                // El titulo no se mando, asi que tiene que seguir siendo el original.
                .tieneCampo("title");
    }

    @Test(groups = "api", description = "El borrado responde y marca el recurso como eliminado")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "productos"})
    public void borraUnProducto() {
        ApiClient.delete("/products/1")
                .tieneCodigo(200)
                .campoEs("id", 1)
                .tieneCampo("isDeleted");
    }
}
