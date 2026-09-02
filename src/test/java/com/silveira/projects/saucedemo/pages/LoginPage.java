package com.silveira.projects.saucedemo.pages;

import com.silveira.helpers.LocatorHelper;
import com.silveira.keywords.WebUI;

/**
 * Página de login.
 *
 * Fijate que no hay un solo By declarado acá: los locators viven en
 * objects/saucedemo.properties y se resuelven por clave. La página solo describe
 * qué se puede hacer, no dónde está cada cosa.
 */
public class LoginPage {

    public void ingresar(String usuario, String password) {
        WebUI.escribirVerificando(LocatorHelper.by("login.usuario"), usuario);
        WebUI.escribirVerificando(LocatorHelper.by("login.password"), password);
        WebUI.click(LocatorHelper.by("login.boton"));
    }

    /** Login que además confirma que se llegó al inventario. */
    public void ingresarYEsperarInventario(String usuario, String password) {
        WebUI.escribirVerificando(LocatorHelper.by("login.usuario"), usuario);
        WebUI.escribirVerificando(LocatorHelper.by("login.password"), password);
        WebUI.clickHasta(LocatorHelper.by("login.boton"),
                WebUI.hastaQueLaUrlContenga("inventory.html"));
    }

    public String mensajeDeError() {
        return WebUI.obtenerTexto(LocatorHelper.by("login.error"));
    }

    public boolean hayError() {
        return WebUI.estaVisible(LocatorHelper.by("login.error"));
    }
}
