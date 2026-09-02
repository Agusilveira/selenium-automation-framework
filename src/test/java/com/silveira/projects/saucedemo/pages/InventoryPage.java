package com.silveira.projects.saucedemo.pages;

import com.silveira.helpers.LocatorHelper;
import com.silveira.keywords.WebUI;

import java.math.BigDecimal;
import java.util.List;

/** Listado de productos: agregar al carrito, ordenar y abrir el carrito. */
public class InventoryPage {

    public boolean estaCargada() {
        return WebUI.estaVisible(LocatorHelper.by("inventario.contenedor"));
    }

    /** "Sauce Labs Backpack" se convierte en el sufijo del data-test del botón. */
    private String slug(String producto) {
        return producto.toLowerCase()
                .replace("(", "").replace(")", "")
                .replace(".", "")
                .replace(" ", "-");
    }

    /**
     * SauceDemo cambia el botón de "Add to cart" a "Remove" cuando el producto
     * entra al carrito. Esperar ese cambio hace que la acción sea síncrona con su
     * propio efecto: sin eso, el paso siguiente lee el carrito antes de tiempo.
     */
    public void agregarAlCarrito(String producto) {
        WebUI.clickHasta(
                LocatorHelper.by("inventario.agregar", slug(producto)),
                WebUI.hastaQueAparezca(LocatorHelper.by("inventario.quitar", slug(producto))));
    }

    public void quitarDelCarrito(String producto) {
        WebUI.clickHasta(
                LocatorHelper.by("inventario.quitar", slug(producto)),
                WebUI.hastaQueAparezca(LocatorHelper.by("inventario.agregar", slug(producto))));
    }

    /** 0 cuando el badge no está: SauceDemo lo oculta con el carrito vacío. */
    public int cantidadEnCarrito() {
        if (WebUI.contar(LocatorHelper.by("inventario.badge")) == 0) return 0;
        return Integer.parseInt(WebUI.obtenerTexto(LocatorHelper.by("inventario.badge")));
    }

    public void abrirCarrito() {
        WebUI.clickHasta(LocatorHelper.by("inventario.carrito"),
                WebUI.hastaQueLaUrlContenga("cart.html"));
    }

    public void ordenarPor(String criterio) {
        String valor = switch (criterio) {
            case "nombre ascendente"  -> "az";
            case "nombre descendente" -> "za";
            case "precio ascendente"  -> "lohi";
            case "precio descendente" -> "hilo";
            default -> throw new IllegalArgumentException("Criterio desconocido: " + criterio);
        };
        WebUI.seleccionarPorValor(LocatorHelper.by("inventario.orden"), valor);
    }

    public List<String> nombresDeProductos() {
        return WebUI.obtenerTextos(LocatorHelper.by("inventario.nombres"));
    }

    public List<BigDecimal> preciosDeProductos() {
        return WebUI.obtenerTextos(LocatorHelper.by("inventario.precios")).stream()
                .map(texto -> new BigDecimal(texto.replace("$", "").trim()))
                .toList();
    }
}
