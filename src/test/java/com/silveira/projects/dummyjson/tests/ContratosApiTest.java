package com.silveira.projects.dummyjson.tests;

import com.silveira.annotations.FrameworkAnnotation;
import com.silveira.api.ApiClient;
import com.silveira.api.ContractGuard;
import com.silveira.api.Paginador;
import com.silveira.common.BaseApiTest;
import com.silveira.projects.dummyjson.models.Producto;
import org.testng.annotations.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ContratosApiTest extends BaseApiTest {

    @Test(groups = "api",
          description = "El contrato del producto sigue siendo compatible con el guardado")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "contratos"})
    public void elContratoDelProductoSigueSiendoCompatible() {
        List<String> rompientes =
                ContractGuard.cambiosRompientes("producto", ApiClient.get("/products/1").esExitosa());

        assertThat(rompientes)
                .as("cambios que romperian a los consumidores de este endpoint")
                .isEmpty();
    }

    @Test(groups = "api",
          description = "El contrato del usuario autenticado sigue siendo compatible")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "contratos"})
    public void elContratoDelUsuarioSigueSiendoCompatible() {
        List<String> rompientes =
                ContractGuard.cambiosRompientes("usuario-autenticado",
                        ApiClient.getAuth("/auth/me").esExitosa());

        assertThat(rompientes)
                .as("cambios que romperian a los consumidores de /auth/me")
                .isEmpty();
    }

    @Test(groups = "api", description = "El paginador recorre todas las paginas sin repetir ni perder")
    @FrameworkAnnotation(autor = "Agustin", categoria = {"api", "paginacion"})
    public void elPaginadorRecorreTodoSinRepetirNiPerder() {
        int total = ApiClient.get("/products").esExitosa().campo("total");

        List<Producto> todos = Paginador.todos("/products", "products", "total", 30, Producto.class);

        assertThat(todos).as("cantidad de productos recuperados").hasSize(total);

        // Que la cantidad coincida no alcanza: un paginador que ignora 'skip' trae
        // la primera pagina N veces y el total da igual. Los ids unicos lo detectan.
        assertThat(todos).extracting(Producto::id)
                .as("los ids deberian ser todos distintos")
                .doesNotHaveDuplicates();
    }
}
