package com.silveira.api;

import com.silveira.config.FrameworkConstants;
import com.silveira.helpers.FileHelper;
import com.silveira.helpers.JsonHelper;
import com.silveira.utils.LogUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Detecta cambios rompientes en el contrato de un endpoint.
 *
 * Un JSON Schema verifica que la respuesta tenga la forma que se espera hoy. Esto
 * responde otra pregunta: ¿la respuesta de hoy sigue siendo compatible con la que
 * había cuando el contrato se acordó?
 *
 * La diferencia importa porque los cambios que rompen a los consumidores son
 * asimétricos. Que aparezca un campo nuevo no rompe a nadie: quien no lo conoce lo
 * ignora. Que desaparezca uno, o que cambie de tipo, rompe a todos los que lo
 * estaban leyendo. Por eso acá solo se reportan las dos últimas.
 *
 * El contrato se guarda como un mapa de "ruta del campo" a "tipo", en
 * src/test/resources/contracts. La primera corrida lo genera; a partir de ahí, se
 * compara. Actualizarlo es borrar el archivo y volver a correr, y eso queda
 * registrado en el historial del repositorio: un contrato que cambia deja rastro.
 */
public final class ContractGuard {

    private ContractGuard() {
    }

    /**
     * Compara la respuesta contra el contrato guardado y devuelve los cambios
     * rompientes. Lista vacía significa compatible.
     */
    public static List<String> cambiosRompientes(String nombre, ApiResponse respuesta) {
        String archivo = FrameworkConstants.RUTA_CONTRACTS + nombre + ".json";
        Map<String, String> actual = aplanar(respuesta.comoMapa());

        if (!FileHelper.existe(archivo)) {
            JsonHelper.escribir(archivo, new TreeMap<>(actual));
            LogUtils.warn("No había contrato para '" + nombre + "'. Se generó uno con "
                    + actual.size() + " campos en " + archivo
                    + ". Revisalo y versionalo: a partir de ahora es la referencia.");
            return List.of();
        }

        Map<String, Object> guardado = JsonHelper.comoMapa(archivo);
        List<String> rompientes = new ArrayList<>();

        for (Map.Entry<String, Object> campo : guardado.entrySet()) {
            String ruta = campo.getKey();
            String tipoEsperado = String.valueOf(campo.getValue());
            String tipoActual = actual.get(ruta);

            if (tipoActual == null) {
                rompientes.add("desapareció el campo '" + ruta + "' (era " + tipoEsperado + ")");
            } else if (!tipoActual.equals(tipoEsperado)) {
                rompientes.add("el campo '" + ruta + "' cambió de " + tipoEsperado
                        + " a " + tipoActual);
            }
        }

        // Los campos nuevos se informan pero no rompen: agregar es compatible.
        actual.keySet().stream()
                .filter(ruta -> !guardado.containsKey(ruta))
                .forEach(ruta -> LogUtils.info(
                        "Campo nuevo en el contrato '" + nombre + "': " + ruta
                        + " (" + actual.get(ruta) + "). No rompe a nadie."));

        return rompientes;
    }

    /** Convierte el JSON en un mapa plano de ruta a tipo: {"user.id": "Integer"}. */
    private static Map<String, String> aplanar(Map<String, Object> json) {
        Map<String, String> plano = new TreeMap<>();
        recorrer("", json, plano);
        return plano;
    }

    @SuppressWarnings("unchecked")
    private static void recorrer(String prefijo, Object valor, Map<String, String> destino) {
        if (valor instanceof Map<?, ?> mapa) {
            mapa.forEach((clave, sub) ->
                    recorrer(prefijo.isEmpty() ? String.valueOf(clave) : prefijo + "." + clave,
                             sub, destino));
        } else if (valor instanceof List<?> lista) {
            // Se mira solo el primer elemento: en una lista homogénea alcanza, y en
            // una heterogénea el contrato no se puede expresar como tipo único.
            destino.put(prefijo, "Array");
            if (!lista.isEmpty()) {
                recorrer(prefijo + "[]", lista.get(0), destino);
            }
        } else {
            destino.put(prefijo, valor == null ? "null" : valor.getClass().getSimpleName());
        }
    }
}
