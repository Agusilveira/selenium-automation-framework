package com.silveira.dataprovider;

import com.silveira.config.FrameworkConstants;
import com.silveira.helpers.ExcelHelper;
import com.silveira.helpers.JsonHelper;
import org.testng.annotations.DataProvider;

import java.util.List;
import java.util.Map;

/**
 * Fuentes de datos para los tests.
 *
 * Cada proveedor entrega un Map por caso en vez de un Object[] posicional: al
 * agregar una columna nueva, los tests que no la usan no se enteran, y el que la
 * usa la pide por nombre. Con arreglos posicionales, cualquier columna nueva
 * rompe todas las firmas.
 */
public final class DataProviderManager {

    private DataProviderManager() {
    }

    @DataProvider(name = "usuariosJson")
    public static Object[][] usuariosJson() {
        return aFilas(JsonHelper.comoLista(FrameworkConstants.RUTA_DATA + "usuarios.json"));
    }

    @DataProvider(name = "usuariosExcel")
    public static Object[][] usuariosExcel() {
        return ExcelHelper.comoDataProvider(
                FrameworkConstants.RUTA_DATA + "usuarios.xlsx", "usuarios");
    }

    @DataProvider(name = "usuariosJsonParalelo", parallel = true)
    public static Object[][] usuariosJsonParalelo() {
        return usuariosJson();
    }

    private static Object[][] aFilas(List<? extends Map<String, ?>> registros) {
        Object[][] datos = new Object[registros.size()][1];
        for (int i = 0; i < registros.size(); i++) {
            datos[i][0] = registros.get(i);
        }
        return datos;
    }
}
