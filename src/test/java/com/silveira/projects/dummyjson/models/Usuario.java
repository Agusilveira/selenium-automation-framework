package com.silveira.projects.dummyjson.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record Usuario(
        Integer id,
        String username,
        String email,
        String firstName,
        String lastName,
        String gender,
        String image
) {
}
