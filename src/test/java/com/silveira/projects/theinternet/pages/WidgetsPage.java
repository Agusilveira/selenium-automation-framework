package com.silveira.projects.theinternet.pages;

import com.silveira.config.ConfigManager;
import com.silveira.helpers.LocatorHelper;
import com.silveira.keywords.AlertUtils;
import com.silveira.keywords.FrameUtils;
import com.silveira.keywords.TableUtils;
import com.silveira.keywords.WebUI;
import com.silveira.keywords.WindowUtils;

import java.util.List;
import java.util.Map;

/**
 * Segundo proyecto del framework, sobre the-internet.
 *
 * No tiene login, no tiene flujo, y cada página es independiente: es
 * deliberadamente distinto a SauceDemo. Si el framework le sirve a las dos, deja
 * de estar moldeado a una.
 *
 * Además es lo que ejercita FrameUtils, TableUtils y WindowUtils, que hasta acá
 * compilaban pero nunca se habían ejecutado.
 */
public class WidgetsPage {

    private void abrir(String path) {
        WebUI.abrirUrl(ConfigManager.get().get("widgets.url") + path);
    }

    // --- Esperas: elemento que aparece tarde ---

    public String textoQueCargaConRetraso() {
        abrir("/dynamic_loading/2");
        WebUI.clickHasta(LocatorHelper.by("ti.dynamic.start"),
                WebUI.hastaQueAparezca(LocatorHelper.by("ti.dynamic.finish")));
        return WebUI.obtenerTexto(LocatorHelper.by("ti.dynamic.finish"));
    }

    // --- FrameUtils: frames anidados ---

    public String textoDelFrame(String... ruta) {
        abrir("/nested_frames");
        return FrameUtils.textoDentroDe(ruta);
    }

    // --- AlertUtils ---

    public String resultadoDeAlerta(String tipo, String accion) {
        abrir("/javascript_alerts");
        WebUI.click(LocatorHelper.by("ti.alerta." + tipo));

        if ("acepto".equals(accion)) {
            AlertUtils.aceptar();
        } else {
            AlertUtils.descartar();
        }
        return WebUI.obtenerTexto(LocatorHelper.by("ti.alerta.resultado"));
    }

    public String textoDelPrompt(String respuesta) {
        abrir("/javascript_alerts");
        WebUI.click(LocatorHelper.by("ti.alerta.prompt"));
        AlertUtils.responder(respuesta);
        return WebUI.obtenerTexto(LocatorHelper.by("ti.alerta.resultado"));
    }

    // --- WindowUtils ---

    /** Abre una ventana nueva, lee su título y vuelve dejando todo cerrado. */
    public String tituloDeLaVentanaNueva() {
        abrir("/windows");
        WebUI.click(LocatorHelper.by("ti.ventanas.link"));
        WindowUtils.cambiarA(1);
        try {
            return WebUI.titulo();
        } finally {
            WindowUtils.cerrarLasDemas();
        }
    }

    public int ventanasAbiertas() {
        return WindowUtils.cantidad();
    }

    // --- TableUtils ---

    private String tabla() {
        return com.silveira.helpers.PropertiesHelper.locator("ti.tabla");
    }

    public List<String> encabezadosDeLaTabla() {
        abrir("/tables");
        return TableUtils.encabezados(tabla());
    }

    public int filasDeLaTabla() {
        abrir("/tables");
        return TableUtils.cantidadDeFilas(tabla());
    }

    public List<String> columnaDeLaTabla(String encabezado) {
        abrir("/tables");
        return TableUtils.columna(tabla(), encabezado);
    }

    public Map<String, String> filaConEmail(String email) {
        abrir("/tables");
        int fila = TableUtils.buscarFila(tabla(), "Email", email);
        return TableUtils.comoMapa(tabla()).get(fila - 1);
    }

    // --- FailureHandling ---

    public void abrirCheckboxes() {
        abrir("/checkboxes");
    }

    /** El banner no existe: es el caso de uso legítimo de OPTIONAL. */
    public boolean intentarCerrarBannerQueNoExiste() {
        return WebUI.click(LocatorHelper.by("ti.banner.inexistente"),
                com.silveira.enums.FailureHandling.OPTIONAL);
    }

    public boolean marcarCheckboxInexistente(com.silveira.enums.FailureHandling manejo) {
        return WebUI.marcar(LocatorHelper.by("ti.banner.inexistente"), manejo);
    }
}
