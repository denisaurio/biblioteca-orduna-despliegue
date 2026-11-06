package com.biblioteca;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

import java.util.TimeZone;

// Se excluye el usuario en memoria de Spring (el que imprime "generated password"): el login es con JWT.
// @ConfigurationPropertiesScan activa las clases @ConfigurationProperties (credenciales de R2, etc).
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class BibliotecaApplication {
    public static void main(String[] args) {
        // Fechas de préstamos, multas y cierres siempre en hora de Perú
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
        SpringApplication.run(BibliotecaApplication.class, args);
    }
}
