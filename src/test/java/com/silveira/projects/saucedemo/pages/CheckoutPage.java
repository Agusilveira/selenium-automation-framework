package com.silveira.projects.saucedemo.pages;

import com.silveira.helpers.LocatorHelper;
import com.silveira.keywords.WebUI;

import java.math.BigDecimal;
import java.util.List;

/** Carrito y checkout, de punta a punta. */
public class CheckoutPage {

    public List<String> itemsDelCarrito() {
        return WebUI.obtenerTextos(LocatorHelper.by("carrito.items"));
    }

    public void iniciarCheckout() {
        WebUI.clickHasta(LocatorHelper.by("carrito.checkout"),
                WebUI.hastaQueLaUrlContenga("checkout-step-one.html"));
    }

    public void completarDatos(String nombre, String apellido, String codigoPostal) {
        WebUI.escribirVerificando(LocatorHelper.by("checkout.nombre"), nombre);
        WebUI.escribirVerificando(LocatorHelper.by("checkout.apellido"), apellido);
        WebUI.escribirVerificando(LocatorHelper.by("checkout.codigoPostal"), codigoPostal);
        WebUI.clickHasta(LocatorHelper.by("checkout.continuar"),
                WebUI.hastaQueLaUrlContenga("checkout-step-two.html"));
    }

    public void confirmar() {
        WebUI.clickHasta(LocatorHelper.by("checkout.finalizar"),
                WebUI.hastaQueLaUrlContenga("checkout-complete.html"));
    }

    public String mensajeDeConfirmacion() {
        return WebUI.obtenerTexto(LocatorHelper.by("checkout.confirmacion"));
    }

    public BigDecimal subtotal()  { return monto("checkout.subtotal"); }
    public BigDecimal impuestos() { return monto("checkout.impuestos"); }
    public BigDecimal total()     { return monto("checkout.total"); }

    /** Extrae el numero de textos como "Item total: $29.99". */
    private BigDecimal monto(String clave) {
        String texto = WebUI.obtenerTexto(LocatorHelper.by(clave));
        int corte = texto.indexOf('$');
        if (corte < 0) {
            throw new IllegalStateException("No hay un monto en el texto: '" + texto + "'");
        }
        return new BigDecimal(texto.substring(corte + 1).trim());
    }
}
