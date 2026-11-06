package com.biblioteca.controller;

import com.biblioteca.exception.OperacionInvalidaException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;

/**
 * Pago con Yape.
 *
 * Yape no tiene API publica con QR dinamico (con el monto puesto), asi que el
 * sistema usa el QR ESTATICO de Yape Negocio: el lector abre la app de Yape, teclea
 * el monto que le pone la pantalla y paga. Despues sube la captura y el personal la
 * aprueba o la deniega.
 *
 * El QR es una imagen publica: se sirve sin token para que el <img> del navegador
 * pueda cargarlo. El monto NO va en el QR, va escrito debajo.
 */
@RestController
@RequestMapping("/api/pagos/yape")
public class PagoYapeController {

    private static final String RECURSO_QR = "qr.jpeg";

    /** GET /api/pagos/yape/qr - el codigo QR de Yape Negocio (imagen). */
    @GetMapping(value = "/qr", produces = MediaType.IMAGE_JPEG_VALUE)
    public ResponseEntity<byte[]> qr() {
        ClassPathResource recurso = new ClassPathResource(RECURSO_QR);
        if (!recurso.exists()) {
            throw new OperacionInvalidaException("No se encontro el codigo QR de Yape en el servidor");
        }
        try (InputStream entrada = recurso.getInputStream()) {
            byte[] bytes = entrada.readAllBytes();
            return ResponseEntity.ok()
                    .contentType(MediaType.IMAGE_JPEG)
                    .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                    .body(bytes);
        } catch (IOException e) {
            throw new OperacionInvalidaException("No se pudo leer el codigo QR de Yape: " + e.getMessage());
        }
    }
}
