package com.silveira.projects.app.pages;

import com.silveira.config.ConfigManager;
import com.silveira.helpers.LocatorHelper;
import com.silveira.keywords.WebUI;

import java.util.List;

/**
 * Interfaz web de la aplicación bajo prueba.
 *
 * Es la tercera cara del mismo sistema: lo que se crea acá se puede consultar por
 * `AppApi` y confirmar con `DatabaseHelper`. Esa coincidencia es lo que permite
 * cruzar capas, y es lo que ninguna de las apps públicas anteriores habilitaba.
 */
public class AppPages {

    private String usuario() { return ConfigManager.get().get("app.usuario"); }
    private String repo()    { return ConfigManager.get().get("app.repo"); }

    private String urlDe(String ruta) {
        return ConfigManager.get().baseUrl() + ruta;
    }

    // --- Sesión ---

    public void ingresar() {
        ingresar(usuario(), ConfigManager.get().get("app.password"));
    }

    public void ingresar(String usuario, String password) {
        WebUI.abrirUrl(urlDe("/user/login"));
        WebUI.escribirVerificando(LocatorHelper.by("app.login.usuario"), usuario);
        WebUI.escribirVerificando(LocatorHelper.by("app.login.password"), password);
        // Gitea redirige al panel de inicio, no al perfil. Lo que define el exito
        // es haber salido de la pagina de login, no a donde se llego.
        WebUI.clickHasta(LocatorHelper.by("app.login.boton"),
                WebUI.hastaQueLaUrlNoContenga("/user/login"));
    }

    public boolean haySesionIniciada() {
        return !WebUI.urlActual().contains("/user/login");
    }

    public String mensajeDeError() {
        return WebUI.obtenerTexto(LocatorHelper.by("app.login.error"));
    }

    // --- Issues ---

    public void abrirListadoDeIssues() {
        WebUI.abrirUrl(urlDe("/" + usuario() + "/" + repo() + "/issues"));
    }

    public List<String> titulosDeIssues() {
        abrirListadoDeIssues();
        if (WebUI.contar(LocatorHelper.by("app.issues.titulos")) == 0) {
            return List.of();
        }
        return WebUI.obtenerTextos(LocatorHelper.by("app.issues.titulos"));
    }

    /** Crea un issue desde la interfaz y devuelve su número, leído de la URL. */
    public int crearIssue(String titulo, String cuerpo) {
        WebUI.abrirUrl(urlDe("/" + usuario() + "/" + repo() + "/issues/new"));
        WebUI.escribirVerificando(LocatorHelper.by("app.issues.campoTitulo"), titulo);
        WebUI.escribir(LocatorHelper.by("app.issues.campoCuerpo"), cuerpo);
        // No sirve esperar que la URL contenga "/issues/": la pagina de creacion ya
        // es /issues/new y la condicion se cumpliria sin que pase nada. Lo que
        // define el exito es haber llegado a /issues/<numero>.
        WebUI.clickHasta(LocatorHelper.by("app.issues.botonCrear"),
                WebUI.hastaQueLaUrlCoincidaCon(".*/issues/[0-9]+$"));

        // El número queda en la URL: .../issues/7
        String url = WebUI.urlActual();
        String cola = url.substring(url.lastIndexOf('/') + 1);
        return Integer.parseInt(cola.replaceAll("[^0-9]", ""));
    }

    /**
     * Cuantos issues abiertos dice la interfaz que hay, segun su propio contador.
     *
     * No se cuentan las filas del listado a proposito: la interfaz pagina de a 20,
     * asi que contar filas mide cuantos entraron en la pagina y no cuantos hay. El
     * contador de la pestaña es lo que la aplicacion afirma, que es justamente lo
     * que tiene sentido contrastar contra la base.
     */
    public int cantidadDeIssuesAbiertos() {
        abrirListadoDeIssues();
        String texto = WebUI.obtenerTexto(LocatorHelper.by("app.issues.contadorAbiertos"));
        return Integer.parseInt(texto.replaceAll("[^0-9]", ""));
    }

    public boolean existeElIssueConTitulo(String titulo) {
        return titulosDeIssues().stream().anyMatch(t -> t.contains(titulo));
    }
}
