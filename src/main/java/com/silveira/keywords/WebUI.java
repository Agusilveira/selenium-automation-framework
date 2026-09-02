package com.silveira.keywords;

import com.silveira.config.FrameworkConstants;
import com.silveira.driver.DriverManager;
import com.silveira.exceptions.FrameworkException;
import com.silveira.utils.LogUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedCondition;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;

import java.util.List;

/**
 * La librería de acciones del framework: lo que usan las páginas y los tests.
 *
 * Cada método hace en una línea lo que a mano son cuatro: espera lo que
 * corresponde, ejecuta, deja registro en el log, y en las acciones que pueden
 * perderse verifica que hayan tenido efecto. Eso es lo que separa a una capa de
 * framework de un envoltorio: WebUI.click(locator) no es element.click() con
 * otro nombre.
 *
 * Convención: todos los métodos reciben By, no WebElement. Un WebElement guardado
 * se vuelve stale en cuanto la página se redibuja; un By se resuelve recién en el
 * momento de usarlo.
 */
public final class WebUI {

    private WebUI() {
    }

    private static WebDriver driver() {
        return DriverManager.get();
    }

    private static JavascriptExecutor js() {
        return (JavascriptExecutor) driver();
    }

    // ------------------------------------------------------------------
    // Navegación
    // ------------------------------------------------------------------

    public static void abrirUrl(String url) {
        LogUtils.info("Abriendo " + url);
        driver().get(url);
    }

    public static void refrescar() {
        LogUtils.info("Refrescando la página");
        driver().navigate().refresh();
    }

    public static void atras() {
        LogUtils.info("Volviendo a la página anterior");
        driver().navigate().back();
    }

    public static void adelante() {
        LogUtils.info("Avanzando a la página siguiente");
        driver().navigate().forward();
    }

    public static String titulo() {
        return driver().getTitle();
    }

    public static String urlActual() {
        return driver().getCurrentUrl();
    }

    // ------------------------------------------------------------------
    // Interacción con elementos
    // ------------------------------------------------------------------

    public static void click(By locator) {
        LogUtils.info("Click en " + locator);
        WaitUtils.clickeable(locator).click();
    }

    /**
     * Click que además confirma su propio efecto, reintenta, y como último recurso
     * usa JavaScript.
     *
     * Un click que no produce efecto no es raro: en algunos entornos —Chrome
     * headless en runners de CI, sobre todo— el evento no llega a la página.
     * Selenium no lanza nada, el elemento está visible, habilitado y sin nada
     * encima, y simplemente no pasa nada. Verificado instrumentando la página con
     * un listener propio: el evento no llega, mientras que un click por JavaScript
     * sobre el mismo elemento sí funciona.
     *
     * El recurso a JavaScript avisa cada vez que se usa. Cuesta fidelidad —no
     * ejercita el mismo camino que el de una persona— así que es el último recurso
     * y no el método por defecto. Si esos avisos se vuelven frecuentes, el problema
     * de entrega volvió y hay que atacarlo, no acostumbrarse.
     */
    public static void clickHasta(By locator, ExpectedCondition<?> efecto) {
        TimeoutException ultimoError = null;

        for (int intento = 1; intento <= FrameworkConstants.INTENTOS_ACCION; intento++) {
            WaitUtils.clickeable(locator).click();
            if (WaitUtils.seCumple(efecto, FrameworkConstants.TIMEOUT_EFECTO_ACCION)) {
                if (intento > 1) LogUtils.warn("Click en " + locator + " necesitó " + intento + " intentos");
                return;
            }
            ultimoError = new TimeoutException("El click en " + locator + " no produjo efecto");
        }

        LogUtils.warn("Click nativo sin efecto en " + locator + ", recurriendo a JavaScript");
        js().executeScript("arguments[0].click();", driver().findElement(locator));
        if (WaitUtils.seCumple(efecto, FrameworkConstants.TIMEOUT_EFECTO_ACCION)) return;
        throw ultimoError;
    }

    public static void clickPorJs(By locator) {
        LogUtils.info("Click por JavaScript en " + locator);
        js().executeScript("arguments[0].click();", WaitUtils.presente(locator));
    }

    public static void dobleClick(By locator) {
        LogUtils.info("Doble click en " + locator);
        new Actions(driver()).doubleClick(WaitUtils.clickeable(locator)).perform();
    }

    public static void clickDerecho(By locator) {
        LogUtils.info("Click derecho en " + locator);
        new Actions(driver()).contextClick(WaitUtils.clickeable(locator)).perform();
    }

    public static void hover(By locator) {
        LogUtils.info("Hover sobre " + locator);
        new Actions(driver()).moveToElement(WaitUtils.visible(locator)).perform();
    }

    public static void arrastrar(By origen, By destino) {
        LogUtils.info("Arrastrando " + origen + " hasta " + destino);
        new Actions(driver())
                .dragAndDrop(WaitUtils.visible(origen), WaitUtils.visible(destino))
                .perform();
    }

    // ------------------------------------------------------------------
    // Texto
    // ------------------------------------------------------------------

    public static void escribir(By locator, String texto) {
        LogUtils.info("Escribiendo en " + locator);
        WaitUtils.clickeable(locator).sendKeys(texto);
    }

    public static void limpiarYEscribir(By locator, String texto) {
        WebElement campo = WaitUtils.clickeable(locator);
        campo.clear();
        campo.sendKeys(texto);
        LogUtils.info("Escrito en " + locator);
    }

    /**
     * Escribe y verifica que el valor haya quedado.
     *
     * El mismo problema de entrega de eventos que afecta al click afecta a
     * sendKeys: los caracteres se pierden sin que nada falle, el formulario queda
     * incompleto y el síntoma aparece varios pasos después, buscando elementos de
     * una página a la que nunca se llegó.
     *
     * El valor de reserva se asigna con el setter nativo de HTMLInputElement más un
     * evento input, no asignando .value: en aplicaciones React una asignación
     * directa no actualiza el estado del componente y el valor se pierde al
     * siguiente redibujado.
     */
    public static void escribirVerificando(By locator, String texto) {
        WebElement campo = WaitUtils.clickeable(locator);
        campo.clear();
        campo.sendKeys(texto);

        if (texto.equals(campo.getDomProperty("value"))) {
            LogUtils.info("Escrito en " + locator);
            return;
        }

        LogUtils.warn("sendKeys sin efecto en " + locator + ", asignando por JavaScript");
        js().executeScript(
                "const setter = Object.getOwnPropertyDescriptor("
              + "    window.HTMLInputElement.prototype, 'value').set;"
              + "setter.call(arguments[0], arguments[1]);"
              + "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));",
                campo, texto);
    }

    public static String obtenerTexto(By locator) {
        return WaitUtils.visible(locator).getText().trim();
    }

    public static String obtenerAtributo(By locator, String atributo) {
        return WaitUtils.presente(locator).getAttribute(atributo);
    }

    public static String obtenerValor(By locator) {
        return WaitUtils.presente(locator).getDomProperty("value");
    }

    public static List<String> obtenerTextos(By locator) {
        return WaitUtils.todosVisibles(locator).stream()
                .map(WebElement::getText)
                .map(String::trim)
                .toList();
    }

    public static void presionarTecla(By locator, Keys tecla) {
        LogUtils.info("Tecla " + tecla + " en " + locator);
        WaitUtils.clickeable(locator).sendKeys(tecla);
    }

    public static void subirArchivo(By locator, String rutaAbsoluta) {
        LogUtils.info("Subiendo archivo a " + locator);
        WaitUtils.presente(locator).sendKeys(rutaAbsoluta);
    }

    // ------------------------------------------------------------------
    // Estado
    // ------------------------------------------------------------------

    /** No lanza si el elemento no aparece: devuelve false. */
    public static boolean estaVisible(By locator) {
        return WaitUtils.estaVisible(locator, FrameworkConstants.TIMEOUT_EFECTO_ACCION);
    }

    public static boolean estaVisible(By locator, int segundos) {
        return WaitUtils.estaVisible(locator, segundos);
    }

    public static boolean estaHabilitado(By locator) {
        return WaitUtils.presente(locator).isEnabled();
    }

    public static boolean estaSeleccionado(By locator) {
        return WaitUtils.presente(locator).isSelected();
    }

    public static int contar(By locator) {
        return driver().findElements(locator).size();
    }

    // ------------------------------------------------------------------
    // Checkbox y radio
    // ------------------------------------------------------------------

    public static void marcar(By locator) {
        WebElement elemento = WaitUtils.clickeable(locator);
        if (!elemento.isSelected()) {
            elemento.click();
            LogUtils.info("Marcado " + locator);
        }
    }

    public static void desmarcar(By locator) {
        WebElement elemento = WaitUtils.clickeable(locator);
        if (elemento.isSelected()) {
            elemento.click();
            LogUtils.info("Desmarcado " + locator);
        }
    }

    // ------------------------------------------------------------------
    // Select
    // ------------------------------------------------------------------

    public static void seleccionarPorTexto(By locator, String texto) {
        LogUtils.info("Seleccionando '" + texto + "' en " + locator);
        new Select(WaitUtils.visible(locator)).selectByVisibleText(texto);
    }

    public static void seleccionarPorValor(By locator, String valor) {
        LogUtils.info("Seleccionando valor '" + valor + "' en " + locator);
        new Select(WaitUtils.visible(locator)).selectByValue(valor);
    }

    public static void seleccionarPorIndice(By locator, int indice) {
        LogUtils.info("Seleccionando índice " + indice + " en " + locator);
        new Select(WaitUtils.visible(locator)).selectByIndex(indice);
    }

    public static String opcionSeleccionada(By locator) {
        return new Select(WaitUtils.visible(locator)).getFirstSelectedOption().getText().trim();
    }

    public static List<String> todasLasOpciones(By locator) {
        return new Select(WaitUtils.visible(locator)).getOptions().stream()
                .map(WebElement::getText)
                .map(String::trim)
                .toList();
    }

    // ------------------------------------------------------------------
    // Scroll
    // ------------------------------------------------------------------

    public static void scrollHasta(By locator) {
        js().executeScript("arguments[0].scrollIntoView({block: 'center'});",
                WaitUtils.presente(locator));
    }

    public static void scrollAlFinal() {
        js().executeScript("window.scrollTo(0, document.body.scrollHeight);");
    }

    public static void scrollAlInicio() {
        js().executeScript("window.scrollTo(0, 0);");
    }

    // ------------------------------------------------------------------
    // Utilidades
    // ------------------------------------------------------------------

    /** Atajo para el efecto más común de clickHasta: que aparezca otro elemento. */
    public static ExpectedCondition<WebElement> hastaQueAparezca(By locator) {
        return ExpectedConditions.visibilityOfElementLocated(locator);
    }

    /** Atajo para el otro efecto común: que cambie la URL. */
    public static ExpectedCondition<Boolean> hastaQueLaUrlContenga(String fragmento) {
        return ExpectedConditions.urlContains(fragmento);
    }

    /**
     * Pausa fija. Existe porque a veces no queda otra —una animación sin señal
     * observable, por ejemplo— pero cada uso es deuda: si aparece en un flujo
     * normal, falta una espera explícita.
     */
    public static void pausa(int milisegundos) {
        LogUtils.warn("Pausa fija de " + milisegundos + " ms: revisar si falta una espera explícita");
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new FrameworkException("Interrumpido durante una pausa", e);
        }
    }
}
