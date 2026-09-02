package com.silveira.helpers;

import com.silveira.exceptions.FrameworkException;
import org.openqa.selenium.By;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Convierte los locators de los archivos de objects/ en objetos By.
 *
 * El formato es "tipo:valor", por ejemplo:
 *
 *   login.boton=css:[data-test='login-button']
 *   tabla.filas=xpath://table[@id='t1']//tr
 *
 * Tener el tipo en el archivo permite cambiar de CSS a XPath sin recompilar, y
 * que alguien sin Java pueda arreglar un selector que se rompió.
 */
public final class LocatorHelper {

    private static final Map<String, Function<String, By>> TIPOS = Map.of(
            "css", By::cssSelector,
            "xpath", By::xpath,
            "id", By::id,
            "name", By::name,
            "class", By::className,
            "tag", By::tagName,
            "link", By::linkText,
            "partiallink", By::partialLinkText
    );

    private static final Map<String, By> CACHE = new ConcurrentHashMap<>();

    private LocatorHelper() {
    }

    /** Busca la clave en todos los archivos de objects/ y devuelve el By. */
    public static By by(String clave) {
        return CACHE.computeIfAbsent(clave, k -> parsear(k, PropertiesHelper.locator(k)));
    }

    /** Locator con parámetros: "producto.boton=css:[data-test='add-{0}']". */
    public static By by(String clave, Object... valores) {
        String definicion = PropertiesHelper.locator(clave);
        for (int i = 0; i < valores.length; i++) {
            definicion = definicion.replace("{" + i + "}", String.valueOf(valores[i]));
        }
        return parsear(clave, definicion);
    }

    /** Visible para tests: permite validar el parseo sin ensuciar objects/. */
    static By parsear(String clave, String definicion) {
        int corte = definicion.indexOf(':');
        if (corte < 1) {
            throw new FrameworkException(
                    "El locator '" + clave + "' debe tener el formato tipo:valor, "
                    + "pero vale '" + definicion + "'. Tipos válidos: " + TIPOS.keySet());
        }

        String tipo = definicion.substring(0, corte).trim().toLowerCase();
        String valor = definicion.substring(corte + 1).trim();

        Function<String, By> constructor = TIPOS.get(tipo);
        if (constructor == null) {
            throw new FrameworkException(
                    "El locator '" + clave + "' usa el tipo desconocido '" + tipo + "'. "
                    + "Tipos válidos: " + TIPOS.keySet());
        }
        if (valor.isEmpty()) {
            throw new FrameworkException("El locator '" + clave + "' no tiene valor después de '" + tipo + ":'");
        }
        return constructor.apply(valor);
    }
}
