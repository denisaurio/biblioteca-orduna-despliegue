package com.biblioteca.service;

import com.biblioteca.dto.*;
import com.biblioteca.entity.*;
import com.biblioteca.exception.AlmacenamientoException;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoDuplicadoException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/** RF01 / RF02 - Catálogo de libros. */
@Service
public class LibroService {

    private static final Logger log = LoggerFactory.getLogger(LibroService.class);

    private final LibroRepository libroRepository;
    private final AutorRepository autorRepository;
    private final EjemplarRepository ejemplarRepository;
    private final GeneroRepository generoRepository;
    private final PrestamoRepository prestamoRepository;
    private final AutorService autorService;
    private final GeneroService generoService;
    private final NotificacionService notificacionService;
    private final ReservaService reservaService;
    private final AlmacenamientoService almacenamientoService;
    private final IsbnService isbnService;
    private final AuditoriaService auditoriaService;

    public LibroService(LibroRepository libroRepository, AutorRepository autorRepository,
                        EjemplarRepository ejemplarRepository, GeneroRepository generoRepository,
                        PrestamoRepository prestamoRepository, AutorService autorService,
                        GeneroService generoService, NotificacionService notificacionService,
                        ReservaService reservaService, AlmacenamientoService almacenamientoService,
                        IsbnService isbnService, AuditoriaService auditoriaService) {
        this.libroRepository = libroRepository;
        this.autorRepository = autorRepository;
        this.ejemplarRepository = ejemplarRepository;
        this.generoRepository = generoRepository;
        this.prestamoRepository = prestamoRepository;
        this.autorService = autorService;
        this.generoService = generoService;
        this.notificacionService = notificacionService;
        this.reservaService = reservaService;
        this.almacenamientoService = almacenamientoService;
        this.isbnService = isbnService;
        this.auditoriaService = auditoriaService;
    }

    /** RF01 - Registrar libro. RecursoDuplicadoException si el ISBN ya existe. Avisa a los lectores. */
    @Transactional
    public LibroResponseDTO registrarLibro(RegistroLibroDTO datos, Long idActor) {
        Libro libro = crearLibro(datos);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.REGISTRAR_LIBRO, "LIBRO", libro.getId(),
                "Registró el libro '" + libro.getTitulo() + "' (ISBN " + libro.getIsbn() + ")");
        return aDTO(libro);
    }

    /**
     * Registro completo: el libro con su portada y su PDF, en una sola operacion.
     * Se usa desde el formulario de alta, que pregunta tipo, precio y datos de la obra.
     * Las copias del libro NO se crean aqui: se dan de alta en el modulo Ejemplares.
     *
     * Los autores y generos que trajo el escaner y que no existian se crean aqui mismo
     * (en la misma transaccion: si el alta falla, tampoco quedan creados).
     */
    @Transactional
    public LibroResponseDTO registrarLibroCompleto(RegistroLibroCompletoDTO datos, MultipartFile portada,
                                                   MultipartFile pdf, Long idActor) {
        String isbn = normalizarIsbn(datos.isbn());
        if (libroRepository.existsByIsbn(isbn)) {
            // Antes de crear autores o subir archivos: un ISBN repetido no deja nada a medias.
            throw new RecursoDuplicadoException("Ya existe un libro con el ISBN " + isbn);
        }
        Libro libro = crearLibro(datos.aRegistroLibro(resolverAutores(datos), resolverGeneros(datos)));
        TipoLibro tipo = datos.tipoEfectivo();

        // Portada: el archivo que sube el bibliotecario o, si no hay, la que salio al escanear el ISBN.
        String aviso = null;
        if (portada != null && !portada.isEmpty()) {
            libro.setUrlImagen(almacenamientoService.subirPortada(portada, libro.getIsbn()));
        } else if (IsbnService.esPortadaDeCatalogo(datos.urlPortada())) {
            aviso = traerPortadaDeInternet(libro, datos.urlPortada());
        }

        // PDF del libro virtual (bucket privado de R2).
        if (pdf != null && !pdf.isEmpty()) {
            libro.setClavePdf(almacenamientoService.subirPdf(pdf, libro.getIsbn()));
        }
        exigirPdfSiEsVirtual(libro);

        crearEjemplares(libro, datos.ejemplares(), tipo);
        libro = libroRepository.save(libro);
        int copias = datos.ejemplares() == null ? 0 : datos.ejemplares().size();
        auditoriaService.registrar(idActor, TipoAccionAuditoria.REGISTRAR_LIBRO, "LIBRO", libro.getId(),
                "Registró el libro '" + libro.getTitulo() + "' (" + tipo + ", "
                        + (copias == 0 ? "sin copias todavia" : copias + " ejemplar(es)") + ")");
        LibroResponseDTO dto = aDTO(libro);
        return aviso == null ? dto : dto.conAviso(aviso);
    }

    /** Editar libro (bibliotecario/gerente). */
    @Transactional
    public LibroResponseDTO actualizarLibro(Long idLibro, RegistroLibroDTO datos, Long idActor) {
        Libro libro = buscar(idLibro);
        String isbn = normalizarIsbn(datos.isbn());
        if (libroRepository.existsByIsbnAndIdNot(isbn, idLibro)) {
            throw new RecursoDuplicadoException("Ya existe otro libro con el ISBN " + isbn);
        }
        copiar(datos, isbn, libro);
        libro = libroRepository.save(libro);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.EDITAR_LIBRO, "LIBRO", libro.getId(),
                "Editó el libro '" + libro.getTitulo() + "' (ISBN " + libro.getIsbn() + ")");
        return aDTO(libro);
    }

    /**
     * Edicion completa: los datos del libro y, si vienen, la portada y el PDF nuevos.
     * Los ejemplares NO se editan aqui: cada ejemplar tiene su propia ficha en el modulo Ejemplares.
     *
     * Portada:
     *   - archivo nuevo -> reemplaza la actual;
     *   - url_portada de un catalogo (se volvio a escanear) -> se baja y reemplaza la actual;
     *   - quitar_portada = true -> el libro se queda sin portada;
     *   - nada de lo anterior -> se queda la que tenia.
     * La portada o el PDF viejos se borran de R2 solo cuando la edicion se confirma.
     */
    @Transactional
    public LibroResponseDTO actualizarLibroCompleto(Long idLibro, RegistroLibroCompletoDTO datos,
                                                     MultipartFile portada, MultipartFile pdf, Long idActor) {
        Libro libro = buscar(idLibro);
        String isbn = normalizarIsbn(datos.isbn());
        if (libroRepository.existsByIsbnAndIdNot(isbn, idLibro)) {
            throw new RecursoDuplicadoException("Ya existe otro libro con el ISBN " + isbn);
        }
        copiar(datos.aRegistroLibro(resolverAutores(datos), resolverGeneros(datos)), isbn, libro);

        String aviso = null;
        String portadaAnterior = libro.getUrlImagen();
        if (portada != null && !portada.isEmpty()) {
            libro.setUrlImagen(almacenamientoService.subirPortada(portada, libro.getIsbn()));
        } else if (IsbnService.esPortadaDeCatalogo(datos.urlPortada())) {
            aviso = traerPortadaDeInternet(libro, datos.urlPortada());
        } else if (datos.quitarPortadaPedido()) {
            libro.setUrlImagen(null);
        }
        if (portadaAnterior != null && !Objects.equals(portadaAnterior, libro.getUrlImagen())) {
            almacenamientoService.eliminarPortadaAlConfirmar(portadaAnterior);
        }

        if (pdf != null && !pdf.isEmpty()) {
            String pdfAnterior = libro.getClavePdf();
            libro.setClavePdf(almacenamientoService.subirPdf(pdf, libro.getIsbn()));
            almacenamientoService.eliminarPdfAlConfirmar(pdfAnterior);
        }
        exigirPdfSiEsVirtual(libro);
        libro = libroRepository.save(libro);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.EDITAR_LIBRO, "LIBRO", libro.getId(),
                "Editó el libro '" + libro.getTitulo() + "' (ISBN " + libro.getIsbn() + ")");
        LibroResponseDTO dto = aDTO(libro);
        return aviso == null ? dto : dto.conAviso(aviso);
    }

    /** Sube (o reemplaza) la portada de un libro ya registrado. */
    @Transactional
    public LibroResponseDTO subirPortada(Long idLibro, MultipartFile archivo, Long idActor) {
        Libro libro = buscar(idLibro);
        String anterior = libro.getUrlImagen();
        libro.setUrlImagen(almacenamientoService.subirPortada(archivo, libro.getIsbn()));
        almacenamientoService.eliminarPortadaAlConfirmar(anterior);
        libro = libroRepository.save(libro);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.SUBIR_PORTADA, "LIBRO", libro.getId(),
                "Subió la portada de '" + libro.getTitulo() + "'");
        return aDTO(libro);
    }

    /** Sube (o reemplaza) el PDF de un libro virtual. */
    @Transactional
    public LibroResponseDTO subirPdf(Long idLibro, MultipartFile archivo, Long idActor) {
        Libro libro = buscar(idLibro);
        if (libro.getTipoLibro() == TipoLibro.FISICO) {
            throw new OperacionInvalidaException("Un libro fisico no lleva PDF: cambia el tipo del libro a virtual o hibrido");
        }
        String anterior = libro.getClavePdf();
        libro.setClavePdf(almacenamientoService.subirPdf(archivo, libro.getIsbn()));
        almacenamientoService.eliminarPdfAlConfirmar(anterior);
        libro = libroRepository.save(libro);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.SUBIR_PDF, "LIBRO", libro.getId(),
                "Subió el PDF de '" + libro.getTitulo() + "'");
        return aDTO(libro);
    }

    /** RF02 - Buscar libros por título, ISBN, autor o género. Criterio vacío = todos. */
    @Transactional(readOnly = true)
    public List<LibroResponseDTO> buscarLibros(String criterio) {
        String q = criterio == null ? "" : criterio.trim();
        return aDTOs(libroRepository.buscar(q));
    }

    @Transactional(readOnly = true)
    public LibroResponseDTO obtenerLibro(Long idLibro) {
        return aDTO(buscar(idLibro));
    }

    /**
     * Datos de un ISBN buscados en internet (escaner de codigo de barras), ya cruzados con
     * la biblioteca: que autores y generos existen, cuales son nuevos y si el libro ya esta
     * registrado.
     *
     * Si ningun catalogo lo conoce pero el libro YA esta en la biblioteca, se devuelven los
     * datos de la biblioteca (para ir directo a agregarle copias).
     *
     * El endpoint es publico, asi que el escaner puede venir sin sesion: solo se audita
     * si hay alguien logueado (el personal que escanea). Por eso la transaccion NO es
     * de solo lectura: la auditoria se escribe en la misma.
     */
    @Transactional
    public LibroEscaneadoDTO buscarPorIsbn(String isbn, Long idActor) {
        String limpio = IsbnService.normalizar(isbn);
        if (!limpio.matches("\\d{9}[\\dX]|\\d{13}")) {
            throw new OperacionInvalidaException("El ISBN debe tener 10 o 13 dígitos (el décimo puede ser X). "
                    + "El que llegó tiene " + limpio.length() + ": revisa que no sobre ni falte un número");
        }
        if (!IsbnService.digitoDeControlValido(limpio)) {
            throw new OperacionInvalidaException("El ISBN " + limpio + " no es válido: el último dígito (de control) "
                    + "no cuadra. Seguramente hay un número cambiado o repetido; revísalo en el código de barras");
        }
        Optional<Libro> existente = buscarPorIsbnOAlterno(limpio);
        IsbnService.FichaIsbn ficha = isbnService.buscar(limpio);

        LibroEscaneadoDTO resultado;
        if (ficha != null) {
            resultado = escaneado(ficha, existente.orElse(null));
        } else if (existente.isPresent()) {
            resultado = desdeLaBiblioteca(existente.get());
        } else {
            throw new RecursoNoEncontradoException(
                    "Ningun catalogo publico (Google Books ni Open Library) reconoce el ISBN " + limpio
                            + ". Registra el libro escribiendo los datos a mano: el ISBN se puede dejar tal cual.");
        }
        if (idActor != null) {
            auditoriaService.registrar(idActor, TipoAccionAuditoria.ESCANEAR_ISBN, "ISBN", null,
                    "Escaneó el ISBN " + limpio + " ('" + resultado.titulo() + "')");
        }
        return resultado;
    }

    /** "Ver libros" de un autor (tabla libros_autores). */
    @Transactional(readOnly = true)
    public List<LibroResponseDTO> librosDeAutor(Long idAutor) {
        if (!autorRepository.existsById(idAutor)) {
            throw new RecursoNoEncontradoException("No existe el autor con id " + idAutor);
        }
        return aDTOs(libroRepository.findByAutoresIdOrderByTituloAsc(idAutor));
    }

    /** "Libros destacados" del Inicio: los más prestados; si faltan, los más recientes. */
    @Transactional(readOnly = true)
    public List<LibroResponseDTO> destacados(int cantidad) {
        LinkedHashSet<Long> ids = new LinkedHashSet<>();
        for (Object[] fila : prestamoRepository.contarPorLibro()) {
            if (ids.size() >= cantidad) break;
            ids.add((Long) fila[0]);
        }
        for (Libro l : libroRepository.findTop6ByOrderByIdDesc()) {
            if (ids.size() >= cantidad) break;
            ids.add(l.getId());
        }
        Map<Long, Libro> porId = new HashMap<>();
        libroRepository.findAllById(ids).forEach(l -> porId.put(l.getId(), l));
        return aDTOs(ids.stream().map(porId::get).filter(Objects::nonNull).toList());
    }

    Libro buscar(Long idLibro) {
        return libroRepository.findById(idLibro)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el libro con id " + idLibro));
    }

    // ---------------- internos ----------------

    /** Crea y guarda el libro. El tipo y los precios se validan aqui. */
    private Libro crearLibro(RegistroLibroDTO datos) {
        String isbn = normalizarIsbn(datos.isbn());
        if (libroRepository.existsByIsbn(isbn)) {
            throw new RecursoDuplicadoException("Ya existe un libro con el ISBN " + isbn);
        }
        Libro libro = new Libro();
        copiar(datos, isbn, libro);
        libro = libroRepository.save(libro);
        notificacionService.notificarNuevoLibro(libro);
        return libro;
    }

    /** Ids de autores elegidos + los autores nuevos (creados ahora o reutilizados por nombre). */
    private List<Long> resolverAutores(RegistroLibroCompletoDTO datos) {
        List<Long> ids = new ArrayList<>(datos.idsAutores() == null ? List.of() : datos.idsAutores());
        for (Long id : autorService.obtenerOCrear(datos.autoresNuevosOVacio())) {
            if (!ids.contains(id)) ids.add(id);
        }
        if (ids.isEmpty()) {
            throw new OperacionInvalidaException("Debe indicar al menos un autor");
        }
        return ids;
    }

    /** Ids de generos elegidos + los generos nuevos (creados ahora o reutilizados por nombre). */
    private List<Long> resolverGeneros(RegistroLibroCompletoDTO datos) {
        List<Long> ids = new ArrayList<>(datos.idsGeneros() == null ? List.of() : datos.idsGeneros());
        for (Long id : generoService.obtenerOCrear(datos.generosNuevosOVacio())) {
            if (!ids.contains(id)) ids.add(id);
        }
        if (ids.isEmpty()) {
            throw new OperacionInvalidaException("Debe indicar al menos un género");
        }
        return ids;
    }

    /**
     * Baja la portada que encontro el escaner y la sube a R2.
     *
     * La portada de internet es OPCIONAL: si el catalogo no la entrega, no es una imagen o
     * R2 no esta configurado, el libro se registra igual y se devuelve un aviso para que el
     * bibliotecario la suba a mano. No se oculta nada: el motivo va en el aviso y en el log.
     * (Un archivo que el bibliotecario elige a mano si es obligatorio: ese error se propaga.)
     */
    private String traerPortadaDeInternet(Libro libro, String url) {
        if (!almacenamientoService.configurado()) {
            return "El libro se guardó sin portada: falta configurar Cloudflare R2 en el archivo .env.";
        }
        try {
            byte[] imagen = isbnService.descargarPortada(url);
            libro.setUrlImagen(almacenamientoService.subirPortadaDescargada(imagen, libro.getIsbn()));
            return null;
        } catch (AlmacenamientoException | OperacionInvalidaException e) {
            log.warn("No se pudo guardar la portada de internet del ISBN {} ({}): {}", libro.getIsbn(), url,
                    e.getMessage());
            return "El libro se guardó, pero no se pudo traer la portada de internet (" + e.getMessage()
                    + "). Puedes subirla a mano desde Editar.";
        }
    }

    /** El libro con ese ISBN o con su equivalente ISBN-10/ISBN-13. */
    private Optional<Libro> buscarPorIsbnOAlterno(String isbn) {
        Optional<Libro> directo = libroRepository.findByIsbn(isbn);
        if (directo.isPresent()) return directo;
        String alterno = IsbnService.alterno(isbn);
        return alterno == null ? Optional.empty() : libroRepository.findByIsbn(alterno);
    }

    private LibroEscaneadoDTO escaneado(IsbnService.FichaIsbn ficha, Libro existente) {
        AutorService.Coincidencias autores = autorService.coincidencias(ficha.autores());

        List<String> pistas = new ArrayList<>(ficha.generos());
        pistas.addAll(ficha.temas());
        List<GeneroResponseDTO> generosExistentes = generoService.existentesEntre(pistas);
        Set<String> yaEstan = new HashSet<>();
        generosExistentes.forEach(g -> yaEstan.add(Nombres.clave(g.nombre())));
        List<String> generosNuevos = ficha.generos().stream()
                .filter(g -> !yaEstan.contains(Nombres.clave(g)))
                .toList();

        return new LibroEscaneadoDTO(ficha.isbn(), ficha.titulo(), ficha.autores(), ficha.editorial(),
                ficha.anioPublicacion(), ficha.numPaginas(), ficha.urlPortada(), ficha.fuente(),
                autores.existentes(), autores.nuevos(), generosExistentes, generosNuevos,
                existente == null ? null : existente.getId(),
                existente == null ? null : existente.getTitulo());
    }

    /** Ningun catalogo conoce el ISBN, pero el libro ya esta registrado: se usan sus datos. */
    private LibroEscaneadoDTO desdeLaBiblioteca(Libro libro) {
        LibroResponseDTO dto = aDTO(libro);
        return new LibroEscaneadoDTO(libro.getIsbn(), libro.getTitulo(),
                dto.autores().stream().map(AutorResponseDTO::nombre).toList(),
                libro.getEditorial(), libro.getAnioPublicacion(), libro.getNumPaginas(),
                dto.urlImagen(), "BIBLIOTECA", dto.autores(), List.of(), dto.generos(), List.of(),
                libro.getId(), libro.getTitulo());
    }

    /** Un libro virtual o hibrido necesita su PDF: sin el, el lector no podria leerlo. */
    private void exigirPdfSiEsVirtual(Libro libro) {
        if (libro.getTipoLibro() != TipoLibro.FISICO
                && (libro.getClavePdf() == null || libro.getClavePdf().isBlank())) {
            throw new OperacionInvalidaException(
                    "Un libro " + (libro.getTipoLibro() == TipoLibro.VIRTUAL ? "virtual" : "hibrido")
                            + " necesita su PDF para que el lector pueda leerlo");
        }
    }

    /**
     * Crea los ejemplares del libro, agrupados bajo el.
     *
     * Un libro se registra como la OBRA ORIGINAL: sus copias se dan de alta en el
     * modulo Ejemplares, asi que la lista puede venir vacia (y se vacia). Lo unico
     * que no tiene sentido son copias de un libro virtual: sus ejemplares son el PDF.
     */
    private void crearEjemplares(Libro libro, List<EjemplarNuevoDTO> ejemplares, TipoLibro tipo) {
        List<EjemplarNuevoDTO> lista = ejemplares == null ? List.of() : ejemplares;
        if (tipo == TipoLibro.VIRTUAL && !lista.isEmpty()) {
            throw new OperacionInvalidaException("Un libro virtual no tiene ejemplares: sus copias son el PDF");
        }
        Set<String> codigos = new HashSet<>();
        for (EjemplarNuevoDTO dato : lista) {
            String codigo = dato.codigoInventario().trim().toUpperCase();
            if (!codigos.add(codigo)) {
                throw new RecursoDuplicadoException("El codigo de inventario " + codigo + " esta repetido en la lista");
            }
            if (ejemplarRepository.existsByCodigoInventario(codigo)) {
                throw new RecursoDuplicadoException("Ya existe un ejemplar con el codigo de inventario " + codigo);
            }
            Ejemplar ejemplar = new Ejemplar();
            ejemplar.setLibro(libro);
            ejemplar.setCodigoInventario(codigo);
            ejemplar.setUbicacion(dato.ubicacion() == null || dato.ubicacion().isBlank()
                    ? null : dato.ubicacion().trim());
            // Cada copia guarda su precio; si no se indica, toma el del libro.
            ejemplar.setPrecio(EjemplarService.precioDe(dato.precio(), libro));
            ejemplar.setEstado(dato.estado() == null ? EstadoEjemplar.DISPONIBLE : dato.estado());
            ejemplar = ejemplarRepository.save(ejemplar);
            // Si alguien esperaba este libro, el ejemplar nuevo se le aparta.
            reservaService.asignarEjemplarLiberado(ejemplar);
        }
    }

    private void copiar(RegistroLibroDTO datos, String isbn, Libro libro) {
        libro.setTitulo(datos.titulo().trim());
        libro.setIsbn(isbn);
        libro.setEditorial(datos.editorial() == null || datos.editorial().isBlank() ? null : datos.editorial().trim());
        libro.setAnioPublicacion(datos.anioPublicacion());
        libro.setNumPaginas(datos.numPaginas());
        aplicarTipoYPrecios(datos.tipoEfectivo(), datos.precioAlquilerDiario(), datos.precioCompra(), libro);

        List<Autor> autores = autorRepository.findAllById(datos.idsAutores());
        if (autores.size() != new HashSet<>(datos.idsAutores()).size()) {
            throw new RecursoNoEncontradoException("Uno o más autores de 'ids_autores' no existen");
        }
        libro.setAutores(new HashSet<>(autores));

        List<Genero> generos = generoRepository.findAllById(datos.idsGeneros());
        if (generos.size() != new HashSet<>(datos.idsGeneros()).size()) {
            throw new RecursoNoEncontradoException("Uno o más géneros de 'ids_generos' no existen");
        }
        libro.setGeneros(new HashSet<>(generos));
    }

    /**
     * Reglas de precio segun el tipo de libro:
     *   - FISICO: sin precio de alquiler. El de compra es el precio del ejemplar en venta.
     *   - VIRTUAL e HIBRIDO: precio de alquiler por dia y precio de compra, y el de
     *     compra tiene que ser mayor (comprar siempre sale mas que un dia de alquiler).
     */
    private void aplicarTipoYPrecios(TipoLibro tipo, BigDecimal precioAlquiler, BigDecimal precioCompra,
                                     Libro libro) {
        libro.setTipoLibro(tipo);
        if (tipo == TipoLibro.FISICO) {
            if (precioAlquiler != null && precioAlquiler.compareTo(BigDecimal.ZERO) > 0) {
                throw new OperacionInvalidaException(
                        "Un libro fisico no tiene precio de alquiler: el alquiler es solo de libros virtuales");
            }
            libro.setPrecioAlquilerDiario(null);
            libro.setPrecioCompra(redondear(precioCompra));
            return;
        }
        if (precioAlquiler == null || precioAlquiler.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OperacionInvalidaException("Un libro virtual necesita el precio de alquiler por dia");
        }
        if (precioCompra == null || precioCompra.compareTo(BigDecimal.ZERO) <= 0) {
            throw new OperacionInvalidaException("Un libro virtual necesita el precio de compra");
        }
        if (precioCompra.compareTo(precioAlquiler) <= 0) {
            throw new OperacionInvalidaException(
                    "El precio de compra debe ser mayor que el precio de alquiler por dia");
        }
        libro.setPrecioAlquilerDiario(redondear(precioAlquiler));
        libro.setPrecioCompra(redondear(precioCompra));
    }

    private static BigDecimal redondear(BigDecimal valor) {
        return valor == null ? null : valor.setScale(2, RoundingMode.HALF_UP);
    }

    LibroResponseDTO aDTO(Libro l) {
        return aDTOs(List.of(l)).get(0);
    }

    /**
     * DTOs de varios libros con las cuentas hechas en bloque: copias por libro y libros por
     * autor salen en DOS consultas en total, no en tres por libro. Importa con un catalogo
     * grande y muchos lectores mirando a la vez.
     */
    List<LibroResponseDTO> aDTOs(List<Libro> libros) {
        if (libros.isEmpty()) return List.of();
        List<Long> ids = libros.stream().map(Libro::getId).toList();

        Map<Long, long[]> copias = new HashMap<>();
        for (Object[] fila : ejemplarRepository.contarPorLibros(ids, EstadoEjemplar.DISPONIBLE)) {
            long total = ((Number) fila[1]).longValue();
            long disponibles = fila[2] == null ? 0 : ((Number) fila[2]).longValue();
            copias.put((Long) fila[0], new long[]{total, disponibles});
        }

        Set<Long> idsAutores = new HashSet<>();
        libros.forEach(l -> l.getAutores().forEach(a -> idsAutores.add(a.getId())));
        Map<Long, Long> librosPorAutor = new HashMap<>();
        if (!idsAutores.isEmpty()) {
            for (Object[] fila : libroRepository.contarPorAutores(idsAutores)) {
                librosPorAutor.put((Long) fila[0], ((Number) fila[1]).longValue());
            }
        }

        List<LibroResponseDTO> resultado = new ArrayList<>(libros.size());
        for (Libro l : libros) {
            List<AutorResponseDTO> autores = l.getAutores().stream()
                    .map(a -> new AutorResponseDTO(a.getId(), a.getNombre(), a.getNacionalidad(), a.getBiografia(),
                            librosPorAutor.getOrDefault(a.getId(), 0L)))
                    .sorted(Comparator.comparing(AutorResponseDTO::nombre))
                    .toList();
            List<GeneroResponseDTO> generos = l.getGeneros().stream()
                    .map(GeneroService::aDTO)
                    .sorted(Comparator.comparing(GeneroResponseDTO::nombre))
                    .toList();
            long[] cuenta = copias.getOrDefault(l.getId(), new long[]{0, 0});
            long total = cuenta[0];
            long disponibles = cuenta[1];
            String estado = total == 0 ? "SIN_EJEMPLARES" : disponibles > 0 ? "DISPONIBLE" : "PRESTADO";
            resultado.add(new LibroResponseDTO(l.getId(), l.getTitulo(), l.getIsbn(), l.getEditorial(),
                    l.getAnioPublicacion(), l.getNumPaginas(), autores, generos, total, disponibles, estado,
                    l.getTipoLibro() == null ? TipoLibro.FISICO.name() : l.getTipoLibro().name(),
                    l.getPrecioAlquilerDiario(), l.getPrecioCompra(),
                    almacenamientoService.urlVisible(l.getUrlImagen()),
                    l.getClavePdf() != null && !l.getClavePdf().isBlank(),
                    null));
        }
        return resultado;
    }

    /** Quita guiones y espacios: "978-612-4" == "9786124". */
    private static String normalizarIsbn(String isbn) {
        return isbn.replaceAll("[\\s-]", "").toUpperCase();
    }
}
