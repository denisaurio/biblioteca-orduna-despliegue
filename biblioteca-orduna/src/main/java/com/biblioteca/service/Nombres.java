package com.biblioteca.service;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Comparar nombres de autores y generos sin que importen tildes, mayusculas ni espacios:
 * "Gabriel García Márquez" == "gabriel garcia  marquez", "Ficción" == "ficcion".
 * Asi el escaner de ISBN no crea un autor o un genero repetido por una tilde.
 */
final class Nombres {
    private Nombres() {}

    static String clave(String nombre) {
        if (nombre == null) return "";
        return Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^\\p{L}\\p{N}]+", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    /** Quita espacios de mas sin tocar tildes ni mayusculas. */
    static String limpio(String nombre) {
        return nombre == null ? "" : nombre.replaceAll("\\s+", " ").trim();
    }
}
