package com.silveira.fixtures;

import com.silveira.api.ApiClient;
import com.silveira.exceptions.FrameworkException;
import com.silveira.projects.dummyjson.models.Producto;
import com.silveira.utils.LogUtils;

import java.util.List;
import java.util.Map;

/**
 * Datos de prueba para otros tests, obtenidos por API.
 *
 * Acá la API no es lo que se prueba: es una herramienta para conseguir datos. Esa
 * distinción es la que mantiene separadas las dos capas.
 *
 * Un test de UI que necesita un producto llama a ProductosFixture.masCaro() y no
 * sabe —ni le importa— que eso salió de HTTP. Si mañana ese dato pasa a venir de
 * una base de datos o de un archivo, cambia este archivo y ningún test se entera.
 * Por eso los métodos hablan en lenguaje de dominio ("el más caro", "uno de la
 * categoría X") y no en lenguaje de endpoints.
 *
 * Si la API no responde, los tests que usan fixtures fallan. Y está bien: su
 * precondición no se cumplió. El mensaje lo dice así, para que nadie lo confunda
 * con un bug de la aplicación bajo prueba.
 */
public final class ProductosFixture {

    private ProductosFixture() {
    }

    public static List<Producto> algunos(int cantidad) {
        return pedir("/products", Map.of("limit", cantidad))
                .comoListaDe("products", Producto.class);
    }

    public static Producto masCaro() {
        List<Producto> productos = pedir("/products",
                Map.of("limit", 100, "sortBy", "price", "order", "desc"))
                .comoListaDe("products", Producto.class);

        if (productos.isEmpty()) {
            throw new FrameworkException(
                    "La API no devolvió productos: la precondición del caso no se cumplió.");
        }
        Producto elegido = productos.get(0);
        LogUtils.info("Fixture: producto más caro -> " + elegido.title() + " ($" + elegido.price() + ")");
        return elegido;
    }

    public static List<String> categorias() {
        return pedir("/products/category-list", Map.of()).response().jsonPath().getList("$", String.class);
    }

    /**
     * Envuelve la llamada para que un fallo se lea como lo que es: una precondición
     * que no se cumplió, no un defecto de la aplicación bajo prueba.
     */
    private static com.silveira.api.ApiResponse pedir(String ruta, Map<String, ?> parametros) {
        try {
            com.silveira.api.ApiResponse respuesta = parametros.isEmpty()
                    ? ApiClient.get(ruta)
                    : ApiClient.get(ruta, parametros);

            if (respuesta.codigo() < 200 || respuesta.codigo() >= 300) {
                throw new FrameworkException(
                        "El fixture no pudo obtener datos de " + ruta + ": la API respondió "
                        + respuesta.codigo() + ". Esto no es un fallo de la aplicación bajo "
                        + "prueba, es una precondición del caso que no se cumplió.");
            }
            return respuesta;

        } catch (FrameworkException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new FrameworkException(
                    "El fixture no pudo obtener datos de " + ruta + ". Esto no es un fallo de "
                    + "la aplicación bajo prueba, es una precondición del caso que no se cumplió.", e);
        }
    }
}
