package com.silveira.utils;

import net.datafaker.Faker;

import java.util.Locale;

/**
 * Datos ficticios para los tests.
 *
 * Los datos aleatorios evitan que un test dependa de que un registro previo siga
 * existiendo, pero tienen un costo: cuando un caso falla, el dato que lo hizo
 * fallar ya no se puede reproducir. Por eso hay una semilla fija opcional, y el
 * dato generado siempre se loguea.
 */
public final class FakeDataUtils {

    private static final Faker FAKER = new Faker(new Locale("es"));

    private FakeDataUtils() {
    }

    public static String nombre()    { return FAKER.name().firstName(); }
    public static String apellido()  { return FAKER.name().lastName(); }
    public static String nombreCompleto() { return FAKER.name().fullName(); }
    public static String empresa()   { return FAKER.company().name(); }
    public static String telefono()  { return FAKER.phoneNumber().phoneNumber(); }
    public static String ciudad()    { return FAKER.address().city(); }
    public static String direccion() { return FAKER.address().streetAddress(); }
    public static String codigoPostal() { return FAKER.address().zipCode(); }

    /** Email único por ejecución: dos casos en paralelo no se pisan. */
    public static String email() {
        String correo = FAKER.internet().username() + System.nanoTime() + "@example.com";
        LogUtils.info("Email generado: " + correo);
        return correo;
    }

    public static String password() {
        return FAKER.internet().password(8, 16, true, true, true);
    }

    public static String texto(int palabras) {
        return String.join(" ", FAKER.lorem().words(palabras));
    }

    public static int numeroEntre(int desde, int hasta) {
        return FAKER.number().numberBetween(desde, hasta);
    }
}
