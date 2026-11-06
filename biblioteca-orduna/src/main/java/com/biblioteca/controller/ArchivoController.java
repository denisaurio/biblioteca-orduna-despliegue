package com.biblioteca.controller;

import com.biblioteca.service.AlmacenamientoService;
import com.biblioteca.service.AlmacenamientoService.ArchivoGuardado;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Portadas servidas por el backend, cuando todavia no hay URL publica de R2.
 *
 * GET /api/archivos/portadas/{isbn}/{archivo}   (formato actual, con version)
 * GET /api/archivos/portadas/{archivo}          (formato antiguo: portadas/{isbn}.jpg)
 *
 * Es publico (lo pide un <img> sin token) y solo lee del bucket de portadas, que de
 * todas formas es publico. Como la clave cambia con cada portada nueva, el navegador
 * la guarda un año y no la vuelve a pedir: el backend casi no trabaja.
 */
@RestController
@RequestMapping("/api/archivos/portadas")
public class ArchivoController {

    private final AlmacenamientoService almacenamientoService;

    public ArchivoController(AlmacenamientoService almacenamientoService) {
        this.almacenamientoService = almacenamientoService;
    }

    @GetMapping("/{carpeta}/{archivo}")
    public ResponseEntity<byte[]> portada(@PathVariable String carpeta, @PathVariable String archivo) {
        return responder(almacenamientoService.leerPortada(carpeta + "/" + archivo));
    }

    @GetMapping("/{archivo}")
    public ResponseEntity<byte[]> portadaAntigua(@PathVariable String archivo) {
        return responder(almacenamientoService.leerPortada(archivo));
    }

    private static ResponseEntity<byte[]> responder(ArchivoGuardado archivo) {
        CacheControl cache = archivo.versionado()
                ? CacheControl.maxAge(Duration.ofDays(365)).cachePublic()
                : CacheControl.maxAge(Duration.ofDays(1)).cachePublic();
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .cacheControl(cache);
        if (archivo.etag() != null) {
            respuesta.eTag(archivo.etag());
        }
        return respuesta.body(archivo.datos());
    }
}
