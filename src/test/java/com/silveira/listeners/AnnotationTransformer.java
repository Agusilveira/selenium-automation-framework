package com.silveira.listeners;

import org.testng.IAnnotationTransformer;
import org.testng.annotations.ITestAnnotation;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

/**
 * Aplica el RetryAnalyzer a todos los tests.
 *
 * Sin esto habria que anotar caso por caso con retryAnalyzer, y el que se olvide
 * queda fuera. Que la politica de reintentos sea del framework y no de cada test
 * es justamente el punto.
 */
public class AnnotationTransformer implements IAnnotationTransformer {

    @Override
    @SuppressWarnings("rawtypes")
    public void transform(ITestAnnotation anotacion, Class clase,
                          Constructor constructor, Method metodo) {
        anotacion.setRetryAnalyzer(RetryAnalyzer.class);
    }
}
