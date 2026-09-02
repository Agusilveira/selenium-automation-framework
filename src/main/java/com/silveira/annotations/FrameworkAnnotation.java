package com.silveira.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Metadatos del caso para los reportes: quien lo escribio y a que area pertenece.
 *
 * Se lee en runtime desde el listener, asi que los reportes pueden agrupar por
 * autor o por categoria sin que el test tenga que hacer nada mas que anotarse.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FrameworkAnnotation {

    String[] autor() default {};

    String[] categoria() default {};

    String descripcion() default "";
}
