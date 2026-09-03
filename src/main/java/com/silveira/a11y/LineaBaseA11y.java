package com.silveira.a11y;

import com.silveira.config.FrameworkConstants;
import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

/**
 * Lo que ya se sabia roto, para poder fallar solo por lo nuevo.
 *
 * El problema de sumar accesibilidad a una aplicacion que ya existe: la primera
 * corrida encuentra decenas de violaciones reales, todas legitimas, y todas
 * anteriores al cambio que se esta probando. Si eso hace fallar la suite, la
 * suite queda roja el primer dia y el equipo aprende a ignorarla. Si no hace
 * fallar nada, la accesibilidad no esta probada.
 *
 * La salida es la misma que ya usa `ContractGuard` con los contratos de API:
 * guardar el estado conocido y fallar unicamente cuando empeora. Lo viejo queda
 * visible en el reporte todo el tiempo; lo nuevo rompe el build.
 *
 * El archivo es `src/test/resources/a11y/<pagina>.properties`, con una linea por
 * regla y la cantidad de elementos que la incumplen. Se versiona a proposito: es
 * la unica forma de que "esto ya estaba" sea una afirmacion verificable y no la
 * memoria de alguien. Se regenera con `-Da11y.actualizar=true`.
 */
public final class LineaBaseA11y {

    private static final String PROPIEDAD_ACTUALIZAR = "a11y.actualizar";

    private LineaBaseA11y() {
    }

    public static boolean enModoActualizacion() {
        return Boolean.parseBoolean(System.getProperty(PROPIEDAD_ACTUALIZAR, "false"));
    }

    private static Path archivo(String pagina) {
        return Path.of(FrameworkConstants.RUTA_A11Y, pagina + ".properties");
    }

    public static boolean existe(String pagina) {
        return Files.exists(archivo(pagina));
    }

    /** Regla -> cantidad de elementos tolerados. Vacio si no hay linea base. */
    public static Map<String, Integer> leer(String pagina) {
        Path ruta = archivo(pagina);
        if (!Files.exists(ruta)) return Map.of();

        Properties p = new Properties();
        try (var in = Files.newInputStream(ruta)) {
            p.load(in);
        } catch (IOException e) {
            throw new FrameworkException("No se pudo leer la linea base " + ruta, e);
        }

        Map<String, Integer> base = new LinkedHashMap<>();
        p.forEach((clave, valor) -> base.put(String.valueOf(clave),
                Integer.parseInt(String.valueOf(valor).trim())));
        return base;
    }

    public static void guardar(String pagina, List<ViolacionA11y> violaciones) {
        Map<String, Integer> ordenadas = new TreeMap<>();
        violaciones.forEach(v -> ordenadas.put(v.regla(), v.cantidad()));

        StringBuilder sb = new StringBuilder();
        sb.append("# Linea base de accesibilidad de '").append(pagina).append("'.\n")
          .append("# Violaciones ya conocidas: la suite falla solo si aparece una regla\n")
          .append("# nueva o si crece la cantidad de elementos de una existente.\n")
          .append("# Regenerar con: mvn test -Da11y.actualizar=true\n");
        ordenadas.forEach((regla, cantidad) -> sb.append(regla).append('=').append(cantidad).append('\n'));

        try {
            Path ruta = archivo(pagina);
            Files.createDirectories(ruta.getParent());
            Files.writeString(ruta, sb.toString(), StandardCharsets.UTF_8);
            LogUtils.warn("Linea base de accesibilidad actualizada: " + ruta
                    + " (" + ordenadas.size() + " reglas). Revisala antes de commitear.");
        } catch (IOException e) {
            throw new FrameworkException("No se pudo escribir la linea base de " + pagina, e);
        }
    }

    /**
     * Lo que empeoro respecto de la linea base. Vacio significa que se puede pasar.
     *
     * Una regla que mejoro no falla, pero se avisa: la linea base queda mas alta de
     * lo necesario y una regresion posterior hasta ese numero pasaria desapercibida.
     */
    public static List<String> regresiones(String pagina, List<ViolacionA11y> violaciones) {
        Map<String, Integer> base = leer(pagina);
        List<String> problemas = new ArrayList<>();

        for (ViolacionA11y v : violaciones) {
            Integer tolerado = base.get(v.regla());
            if (tolerado == null) {
                problemas.add("regla nueva '" + v.regla() + "' (" + v.impacto() + ", "
                        + v.cantidad() + " elementos): " + v.ayuda()
                        + " -> " + v.ayudaUrl());
            } else if (v.cantidad() > tolerado) {
                problemas.add("la regla '" + v.regla() + "' pasa de " + tolerado
                        + " a " + v.cantidad() + " elementos: " + v.ayuda());
            } else if (v.cantidad() < tolerado) {
                LogUtils.info("Accesibilidad: '" + v.regla() + "' mejoro de " + tolerado
                        + " a " + v.cantidad() + " en '" + pagina
                        + "'. Conviene bajar la linea base para no perder el terreno ganado.");
            }
        }

        violaciones.stream().map(ViolacionA11y::regla).toList().forEach(base::remove);
        base.keySet().forEach(regla -> LogUtils.info("Accesibilidad: la regla '" + regla
                + "' ya no aparece en '" + pagina + "'. Se puede sacar de la linea base."));

        return problemas;
    }
}
