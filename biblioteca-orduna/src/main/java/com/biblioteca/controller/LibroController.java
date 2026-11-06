package com.biblioteca.controller;

import com.biblioteca.dto.LibroEscaneadoDTO;
import com.biblioteca.dto.LibroResponseDTO;
import com.biblioteca.dto.RegistroLibroCompletoDTO;
import com.biblioteca.dto.RegistroLibroDTO;
import com.biblioteca.security.UsuarioAutenticado;
import com.biblioteca.service.LibroService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** Módulo Catálogo - Libros (RF01, RF02). GET público; POST/PUT bibliotecario/gerente. */
@RestController
@RequestMapping("/api/libros")
public class LibroController {

    private final LibroService libroService;

    public LibroController(LibroService libroService) {
        this.libroService = libroService;
    }

    /** RF01 - POST /api/libros */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LibroResponseDTO registrar(@Valid @RequestBody RegistroLibroDTO datos,
                                      @AuthenticationPrincipal UsuarioAutenticado actor) {
        return libroService.registrarLibro(datos, actor.id());
    }

    /**
     * Registro completo: el libro con sus ejemplares, su portada y su PDF en un solo envio.
     * multipart/form-data con tres partes: "libro" (JSON), "portada" (archivo) y "pdf" (archivo).
     */
    @PostMapping(value = "/completo", consumes = "multipart/form-data")
    @ResponseStatus(HttpStatus.CREATED)
    public LibroResponseDTO registrarCompleto(@RequestPart("libro") @Valid RegistroLibroCompletoDTO datos,
                                              @RequestPart(value = "portada", required = false) MultipartFile portada,
                                              @RequestPart(value = "pdf", required = false) MultipartFile pdf,
                                              @AuthenticationPrincipal UsuarioAutenticado actor) {
        return libroService.registrarLibroCompleto(datos, portada, pdf, actor.id());
    }

    /** PUT /api/libros/{idLibro} - editar */
    @PutMapping("/{idLibro}")
    public LibroResponseDTO actualizar(@PathVariable Long idLibro, @Valid @RequestBody RegistroLibroDTO datos,
                                       @AuthenticationPrincipal UsuarioAutenticado actor) {
        return libroService.actualizarLibro(idLibro, datos, actor.id());
    }

    /**
     * Edicion completa: los datos del libro y, si vienen, la portada y el PDF nuevos.
     * Los ejemplares se editan en el modulo Ejemplares, no aqui.
     */
    @PutMapping(value = "/{idLibro}/completo", consumes = "multipart/form-data")
    public LibroResponseDTO actualizarCompleto(@PathVariable Long idLibro,
                                                @RequestPart("libro") @Valid RegistroLibroCompletoDTO datos,
                                                @RequestPart(value = "portada", required = false) MultipartFile portada,
                                                @RequestPart(value = "pdf", required = false) MultipartFile pdf,
                                                @AuthenticationPrincipal UsuarioAutenticado actor) {
        return libroService.actualizarLibroCompleto(idLibro, datos, portada, pdf, actor.id());
    }

    /**
     * GET /api/libros/isbn/{isbn} - datos del libro buscados por ISBN (escaner de codigo de barras).
     * Llena el formulario solo y trae la portada que se encontro en internet.
     * Es publico, asi que el actor puede venir null (solo se audita si hay sesion).
     */
    @GetMapping("/isbn/{isbn}")
    public LibroEscaneadoDTO buscarPorIsbn(@PathVariable String isbn,
                                           @AuthenticationPrincipal UsuarioAutenticado actor) {
        return libroService.buscarPorIsbn(isbn, actor == null ? null : actor.id());
    }

    /** POST /api/libros/{idLibro}/portada - sube (o reemplaza) la portada del libro. */
    @PostMapping(value = "/{idLibro}/portada", consumes = "multipart/form-data")
    public LibroResponseDTO subirPortada(@PathVariable Long idLibro, @RequestPart("portada") MultipartFile portada,
                                         @AuthenticationPrincipal UsuarioAutenticado actor) {
        return libroService.subirPortada(idLibro, portada, actor.id());
    }

    /** POST /api/libros/{idLibro}/pdf - sube (o reemplaza) el PDF del libro virtual. */
    @PostMapping(value = "/{idLibro}/pdf", consumes = "multipart/form-data")
    public LibroResponseDTO subirPdf(@PathVariable Long idLibro, @RequestPart("pdf") MultipartFile pdf,
                                     @AuthenticationPrincipal UsuarioAutenticado actor) {
        return libroService.subirPdf(idLibro, pdf, actor.id());
    }

    /** RF02 - GET /api/libros/buscar?q=ciro  (por título, ISBN, autor o género) */
    @GetMapping("/buscar")
    public List<LibroResponseDTO> buscar(@RequestParam(name = "q", required = false, defaultValue = "") String criterio) {
        return libroService.buscarLibros(criterio);
    }

    /** GET /api/libros - todo el catálogo */
    @GetMapping
    public List<LibroResponseDTO> listar() {
        return libroService.buscarLibros("");
    }

    /** GET /api/libros/destacados - para el Inicio (más prestados) */
    @GetMapping("/destacados")
    public List<LibroResponseDTO> destacados() {
        return libroService.destacados(6);
    }

    /** GET /api/libros/{idLibro} - "Ver detalles" */
    @GetMapping("/{idLibro}")
    public LibroResponseDTO obtener(@PathVariable Long idLibro) {
        return libroService.obtenerLibro(idLibro);
    }
}
