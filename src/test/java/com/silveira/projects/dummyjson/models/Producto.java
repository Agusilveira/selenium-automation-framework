package com.silveira.projects.dummyjson.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Modelo de producto.
 *
 * Es un record y no una clase con getters porque para un DTO de respuesta no hace
 * falta nada mas: es inmutable, tiene equals y toString gratis, y el toString es
 * lo que aparece en el mensaje cuando una asercion falla.
 *
 * @JsonIgnoreProperties(ignoreUnknown = true) a proposito: si la API agrega un
 * campo, los tests no tienen por que romperse. Los campos que SI importan se
 * verifican con el JSON Schema, que es el lugar correcto para eso.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Producto(
        Integer id,
        String title,
        String description,
        Double price,
        Double rating,
        Integer stock,
        String category,
        List<String> tags
) {
}
