package com.biblioteca.service;

import com.biblioteca.exception.AlmacenamientoException;
import com.biblioteca.exception.OperacionInvalidaException;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Busca los datos de un libro por su ISBN (el escaner de codigo de barras del mostrador).
 *
 * Pregunta A LA VEZ a Google Books y a Open Library (las dos son publicas y gratis: no
 * hace falta ninguna clave) y junta lo que diga cada una:
 *   - titulo, autores, editorial, año y paginas: manda Google Books y Open Library
 *     rellena lo que falte;
 *   - portada: la grande de Open Library si existe (mejor resolucion); si no, la de Google;
 *   - generos: las categorias de Google (traducidas al español) y los temas de Open Library.
 *
 * Si con el ISBN-13 no aparece nada, se prueba con el ISBN-10 equivalente (y al reves):
 * muchos libros antiguos solo estan catalogados con uno de los dos.
 */
@Service
public class IsbnService {

    private static final Logger log = LoggerFactory.getLogger(IsbnService.class);

    private static final String GOOGLE_BOOKS = "https://www.googleapis.com/books/v1/volumes?q=isbn:";
    private static final String OPEN_LIBRARY = "https://openlibrary.org/api/books?bibkeys=ISBN:";
    /** Otro indice de Open Library: a veces tiene ediciones que /api/books no devuelve. */
    private static final String OPEN_LIBRARY_BUSQUEDA = "https://openlibrary.org/search.json?isbn=";

    /** Solo se baja la portada de estos sitios: evita que el backend pida URLs inventadas. */
    private static final Set<String> HOSTS_PERMITIDOS = Set.of(
            "books.google.com",
            "books.googleusercontent.com",
            "covers.openlibrary.org",
            "covers.archive.org");

    private static final long MAX_PORTADA_BYTES = 5L * 1024 * 1024;
    /** Una imagen de menos de 1 KB es el "sin portada" de los catalogos, no una portada. */
    private static final int MIN_PORTADA_BYTES = 1024;

    /** Categorias de Google Books (en ingles) -> genero en español. */
    private static final Map<String, String> CATEGORIAS = Map.ofEntries(
            Map.entry("fiction", "Ficción"),
            Map.entry("juvenile fiction", "Literatura juvenil"),
            Map.entry("juvenile nonfiction", "Infantil y juvenil"),
            Map.entry("young adult fiction", "Literatura juvenil"),
            Map.entry("history", "Historia"),
            Map.entry("science", "Ciencia"),
            Map.entry("computers", "Informática"),
            Map.entry("mathematics", "Matemáticas"),
            Map.entry("business & economics", "Economía y negocios"),
            Map.entry("biography & autobiography", "Biografía"),
            Map.entry("philosophy", "Filosofía"),
            Map.entry("psychology", "Psicología"),
            Map.entry("religion", "Religión"),
            Map.entry("poetry", "Poesía"),
            Map.entry("drama", "Teatro"),
            Map.entry("literary criticism", "Crítica literaria"),
            Map.entry("literary collections", "Antologías"),
            Map.entry("education", "Educación"),
            Map.entry("medical", "Medicina"),
            Map.entry("law", "Derecho"),
            Map.entry("social science", "Ciencias sociales"),
            Map.entry("political science", "Ciencias políticas"),
            Map.entry("art", "Arte"),
            Map.entry("language arts & disciplines", "Lengua y literatura"),
            Map.entry("foreign language study", "Idiomas"),
            Map.entry("cooking", "Cocina"),
            Map.entry("self-help", "Autoayuda"),
            Map.entry("technology & engineering", "Tecnología e ingeniería"),
            Map.entry("comics & graphic novels", "Cómics"),
            Map.entry("travel", "Viajes"),
            Map.entry("sports & recreation", "Deportes"),
            Map.entry("nature", "Naturaleza"),
            Map.entry("health & fitness", "Salud"),
            Map.entry("family & relationships", "Familia"),
            Map.entry("music", "Música"),
            Map.entry("reference", "Consulta"),
            Map.entry("study aids", "Material de estudio"),
            Map.entry("humor", "Humor"),
            Map.entry("true crime", "Policial"),
            Map.entry("architecture", "Arquitectura"),
            Map.entry("performing arts", "Artes escénicas"),
            Map.entry("photography", "Fotografía"));

    private final RestClient restClient;
    /** Dos consultas a la vez (Google y Open Library) por cada escaneo. */
    private final ExecutorService hilos = Executors.newFixedThreadPool(4);
    /**
     * Clave de Google Books (opcional, gratis). Sin clave Google comparte un cupo diario
     * entre todos los que llaman desde la misma IP: en un VPS se agota rapido y Google
     * responde 429. Con clave, el cupo es propio (1000 consultas al dia gratis).
     */
    private final String claveGoogle;

    public IsbnService(RestClient.Builder builder, @Value("${isbn.google-api-key:}") String claveGoogle) {
        this.claveGoogle = claveGoogle == null ? "" : claveGoogle.trim();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(5).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(8).toMillis());
        this.restClient = builder
                .requestFactory(factory)
                .defaultHeader("User-Agent", "BibliotecaOrduna/1.0 (sistema de biblioteca)")
                .build();
    }

    @PreDestroy
    void cerrar() {
        hilos.shutdownNow();
    }

    /**
     * Busca un ISBN ya normalizado. Devuelve null si ningun sitio conoce ese ISBN
     * (no es un error: el bibliotecario escribe los datos a mano).
     */
    public FichaIsbn buscar(String isbn) {
        String limpio = normalizar(isbn);
        String alterno = alterno(limpio);

        CompletableFuture<FichaIsbn> google = CompletableFuture.supplyAsync(() -> {
            FichaIsbn f = buscarEnGoogle(limpio, alterno);
            return f != null || alterno == null ? f : buscarEnGoogle(alterno, limpio);
        }, hilos);
        CompletableFuture<FichaIsbn> openLibrary = CompletableFuture.supplyAsync(() -> {
            FichaIsbn f = buscarEnOpenLibrary(limpio);
            if (f == null && alterno != null) f = buscarEnOpenLibrary(alterno);
            return f != null ? f : buscarEnBusquedaOpenLibrary(limpio);
        }, hilos);

        FichaIsbn g = google.join();
        FichaIsbn o = openLibrary.join();
        if (g == null && o == null) return null;
        if (g == null) return o.conIsbn(limpio);
        if (o == null) return g.conIsbn(limpio);
        return juntar(g, o).conIsbn(limpio);
    }

    /**
     * Baja una portada de internet. Solo acepta los sitios de libros conocidos y solo
     * HTTPS, para que el backend no se convierta en un descargador libre.
     */
    public byte[] descargarPortada(String url) {
        if (url == null || url.isBlank()) {
            throw new OperacionInvalidaException("No se indicó la dirección de la portada");
        }
        URI uri = uriPermitida(url);
        if (uri == null) {
            throw new OperacionInvalidaException("No se puede descargar la portada desde ese sitio");
        }
        try {
            byte[] datos = restClient.get().uri(uri).retrieve().body(byte[].class);
            if (datos == null || datos.length < MIN_PORTADA_BYTES) {
                throw new AlmacenamientoException("El catalogo no tiene una portada real para este libro");
            }
            if (datos.length > MAX_PORTADA_BYTES) {
                throw new AlmacenamientoException("La portada descargada supera los 5 MB");
            }
            return datos;
        } catch (RestClientException e) {
            throw new AlmacenamientoException("No se pudo descargar la portada de internet: " + e.getMessage(), e);
        }
    }

    /** true si la URL es de un catalogo de portadas conocido (la que guarda el escaner). */
    public static boolean esPortadaDeCatalogo(String url) {
        return url != null && !url.isBlank() && uriPermitida(url) != null;
    }

    /** Normaliza el ISBN: sin guiones ni espacios y en mayusculas. */
    public static String normalizar(String isbn) {
        return isbn == null ? "" : isbn.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /**
     * Comprueba el digito de control del ISBN (el ultimo). Si no cuadra, el numero esta
     * mal escrito o mal leido: un digito de mas, de menos o cambiado de lugar.
     */
    public static boolean digitoDeControlValido(String isbn) {
        if (isbn.matches("\\d{13}")) {
            int suma = 0;
            for (int i = 0; i < 13; i++) suma += (isbn.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
            return suma % 10 == 0;
        }
        if (isbn.matches("\\d{9}[\\dX]")) {
            int suma = 0;
            for (int i = 0; i < 10; i++) {
                int d = isbn.charAt(i) == 'X' ? 10 : isbn.charAt(i) - '0';
                suma += (10 - i) * d;
            }
            return suma % 11 == 0;
        }
        return false;
    }

    /** ISBN-13 (978...) -> ISBN-10 y ISBN-10 -> ISBN-13. null si no hay equivalente. */
    static String alterno(String isbn) {
        if (isbn.matches("978\\d{10}")) {
            String nucleo = isbn.substring(3, 12);
            int suma = 0;
            for (int i = 0; i < 9; i++) suma += (10 - i) * (nucleo.charAt(i) - '0');
            int control = (11 - suma % 11) % 11;
            return nucleo + (control == 10 ? "X" : String.valueOf(control));
        }
        if (isbn.matches("\\d{9}[\\dX]")) {
            String nucleo = "978" + isbn.substring(0, 9);
            int suma = 0;
            for (int i = 0; i < 12; i++) suma += (nucleo.charAt(i) - '0') * (i % 2 == 0 ? 1 : 3);
            return nucleo + ((10 - suma % 10) % 10);
        }
        return null;
    }

    // ---------------- Google Books ----------------

    @SuppressWarnings("unchecked")
    private FichaIsbn buscarEnGoogle(String isbn, String alterno) {
        try {
            Map<String, Object> respuesta = restClient.get()
                    .uri(GOOGLE_BOOKS + isbn + "&country=PE&maxResults=5&printType=books")
                    // La clave va en cabecera (no en la URL) para que nunca salga en un log.
                    .headers(h -> { if (!claveGoogle.isEmpty()) h.set("X-Goog-Api-Key", claveGoogle); })
                    .retrieve()
                    .body(Map.class);
            if (respuesta == null || !(respuesta.get("items") instanceof List<?> lista) || lista.isEmpty()) {
                return null;
            }
            // Se elige el resultado que de verdad tiene ese ISBN (Google a veces trae parecidos).
            Map<String, Object> info = null;
            for (Object item : lista) {
                Map<String, Object> vi = volumeInfo(item);
                if (vi != null && tieneIsbn(vi, isbn, alterno)) {
                    info = vi;
                    break;
                }
            }
            if (info == null && lista.size() == 1) info = volumeInfo(lista.get(0));
            if (info == null) return null;

            String titulo = tituloCompleto(texto(info.get("title")), texto(info.get("subtitle")));
            if (titulo.isBlank()) return null;

            List<String> autores = new ArrayList<>();
            if (info.get("authors") instanceof List<?> listaAutores) {
                for (Object a : listaAutores) {
                    String nombre = limpiarNombre(texto(a));
                    if (!nombre.isBlank()) autores.add(nombre);
                }
            }
            List<String> generos = new ArrayList<>();
            if (info.get("categories") instanceof List<?> categorias) {
                for (Object c : categorias) {
                    String genero = traducirCategoria(texto(c));
                    if (genero != null && !generos.contains(genero)) generos.add(genero);
                }
            }
            return new FichaIsbn(isbn, titulo, autores, recortar(texto(info.get("publisher")), 120),
                    anioDe(texto(info.get("publishedDate"))), numero(info.get("pageCount")),
                    portadaDeGoogle(info.get("imageLinks")), generos, List.of(), "GOOGLE_BOOKS");
        } catch (RestClientException e) {
            log.info("Google Books no devolvio datos para el ISBN {}: {}", isbn, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> volumeInfo(Object item) {
        if (!(item instanceof Map<?, ?> m)) return null;
        return m.get("volumeInfo") instanceof Map<?, ?> vi ? (Map<String, Object>) vi : null;
    }

    private static boolean tieneIsbn(Map<String, Object> info, String isbn, String alterno) {
        if (!(info.get("industryIdentifiers") instanceof List<?> ids)) return false;
        for (Object o : ids) {
            if (o instanceof Map<?, ?> m) {
                String valor = normalizar(texto(m.get("identifier")));
                if (valor.equals(isbn) || valor.equals(alterno)) return true;
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static String portadaDeGoogle(Object imageLinks) {
        if (!(imageLinks instanceof Map<?, ?> mapa)) return null;
        Map<String, Object> enlaces = (Map<String, Object>) mapa;
        String url = "";
        for (String tam : List.of("large", "medium", "small", "thumbnail", "smallThumbnail")) {
            url = texto(enlaces.get(tam));
            if (!url.isBlank()) break;
        }
        if (url.isBlank()) return null;
        if (url.startsWith("http://")) url = "https://" + url.substring("http://".length());
        // &edge=curl dibuja una esquina doblada encima de la portada: se quita.
        return url.replace("&edge=curl", "");
    }

    // ---------------- Open Library ----------------

    @SuppressWarnings("unchecked")
    private FichaIsbn buscarEnOpenLibrary(String isbn) {
        try {
            Map<String, Object> respuesta = restClient.get()
                    .uri(OPEN_LIBRARY + isbn + "&format=json&jscmd=data")
                    .retrieve()
                    .body(Map.class);
            if (respuesta == null) return null;
            Map<String, Object> ficha = respuesta.get("ISBN:" + isbn) instanceof Map<?, ?> m
                    ? (Map<String, Object>) m : null;
            if (ficha == null) return null;

            String titulo = tituloCompleto(texto(ficha.get("title")), texto(ficha.get("subtitle")));
            if (titulo.isBlank()) return null;

            List<String> autores = new ArrayList<>();
            if (ficha.get("authors") instanceof List<?> listaAutores) {
                for (Object a : listaAutores) {
                    if (a instanceof Map<?, ?> ma) {
                        String nombre = limpiarNombre(texto(ma.get("name")));
                        if (!nombre.isBlank()) autores.add(nombre);
                    }
                }
            }
            String editorial = "";
            if (ficha.get("publishers") instanceof List<?> editoriales && !editoriales.isEmpty()
                    && editoriales.get(0) instanceof Map<?, ?> me) {
                editorial = texto(me.get("name"));
            }
            // Temas de Open Library: muchos y con ruido. Solo los que estan en el diccionario
            // pasan a ser generos sugeridos; el resto sirve para reconocer generos que ya existen.
            List<String> temas = new ArrayList<>();
            List<String> generos = new ArrayList<>();
            if (ficha.get("subjects") instanceof List<?> lista) {
                for (Object s : lista) {
                    String nombre = s instanceof Map<?, ?> ms ? texto(ms.get("name")) : texto(s);
                    if (nombre.isBlank() || temas.size() >= 20) continue;
                    temas.add(recortar(nombre, 80));
                    String traducido = CATEGORIAS.get(nombre.toLowerCase(Locale.ROOT));
                    if (traducido != null && !generos.contains(traducido) && generos.size() < 3) {
                        generos.add(traducido);
                        temas.add(traducido);
                    }
                }
            }
            String portada = null;
            if (ficha.get("cover") instanceof Map<?, ?> cover) {
                for (String tam : List.of("large", "medium", "small")) {
                    String url = texto(cover.get(tam));
                    if (!url.isBlank()) {
                        portada = url.startsWith("http://") ? "https://" + url.substring(7) : url;
                        break;
                    }
                }
            }
            return new FichaIsbn(isbn, titulo, autores, recortar(editorial, 120),
                    anioDe(texto(ficha.get("publish_date"))), numero(ficha.get("number_of_pages")),
                    portada, generos, temas, "OPEN_LIBRARY");
        } catch (RestClientException e) {
            log.info("Open Library no devolvio datos para el ISBN {}: {}", isbn, e.getMessage());
            return null;
        }
    }

    /** Ultimo intento: el buscador de Open Library (search.json). */
    @SuppressWarnings("unchecked")
    private FichaIsbn buscarEnBusquedaOpenLibrary(String isbn) {
        try {
            Map<String, Object> respuesta = restClient.get()
                    .uri(OPEN_LIBRARY_BUSQUEDA + isbn
                            + "&limit=1&fields=title,subtitle,author_name,publisher,first_publish_year,"
                            + "number_of_pages_median,cover_i,subject")
                    .retrieve()
                    .body(Map.class);
            if (respuesta == null || !(respuesta.get("docs") instanceof List<?> docs) || docs.isEmpty()
                    || !(docs.get(0) instanceof Map<?, ?> doc)) {
                return null;
            }
            String titulo = tituloCompleto(texto(doc.get("title")), texto(doc.get("subtitle")));
            if (titulo.isBlank()) return null;
            List<String> autores = new ArrayList<>();
            if (doc.get("author_name") instanceof List<?> nombres) {
                for (Object n : nombres) {
                    String nombre = limpiarNombre(texto(n));
                    if (!nombre.isBlank() && !autores.contains(nombre)) autores.add(nombre);
                }
            }
            String editorial = doc.get("publisher") instanceof List<?> eds && !eds.isEmpty() ? texto(eds.get(0)) : "";
            List<String> temas = new ArrayList<>();
            List<String> generos = new ArrayList<>();
            if (doc.get("subject") instanceof List<?> lista) {
                for (Object s : lista) {
                    String nombre = texto(s);
                    if (nombre.isBlank() || temas.size() >= 20) continue;
                    temas.add(recortar(nombre, 80));
                    String traducido = CATEGORIAS.get(nombre.toLowerCase(Locale.ROOT));
                    if (traducido != null && !generos.contains(traducido) && generos.size() < 3) {
                        generos.add(traducido);
                        temas.add(traducido);
                    }
                }
            }
            Integer portadaId = numero(doc.get("cover_i"));
            String portada = portadaId == null ? null : "https://covers.openlibrary.org/b/id/" + portadaId + "-L.jpg";
            return new FichaIsbn(isbn, titulo, autores, recortar(editorial, 120),
                    numero(doc.get("first_publish_year")), numero(doc.get("number_of_pages_median")),
                    portada, generos, temas, "OPEN_LIBRARY");
        } catch (RestClientException e) {
            log.info("El buscador de Open Library no devolvio datos para el ISBN {}: {}", isbn, e.getMessage());
            return null;
        }
    }

    // ---------------- juntar las dos fuentes ----------------

    /** Google manda en los datos; Open Library rellena huecos y aporta la portada grande. */
    private static FichaIsbn juntar(FichaIsbn g, FichaIsbn o) {
        String portada = o.urlPortada() != null ? o.urlPortada() : g.urlPortada();
        return new FichaIsbn(g.isbn(),
                g.titulo(),
                g.autores().isEmpty() ? o.autores() : g.autores(),
                g.editorial().isBlank() ? o.editorial() : g.editorial(),
                g.anioPublicacion() != null ? g.anioPublicacion() : o.anioPublicacion(),
                g.numPaginas() != null ? g.numPaginas() : o.numPaginas(),
                portada,
                g.generos().isEmpty() ? o.generos() : g.generos(),
                o.temas(),
                "GOOGLE_BOOKS+OPEN_LIBRARY");
    }

    // ---------------- utilidades ----------------

    private static URI uriPermitida(String url) {
        String segura = url.trim();
        if (segura.startsWith("http://")) {
            segura = "https://" + segura.substring("http://".length());
        }
        try {
            URI uri = URI.create(segura);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null
                    || !HOSTS_PERMITIDOS.contains(host.toLowerCase(Locale.ROOT))) {
                return null;
            }
            return uri;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String traducirCategoria(String categoria) {
        if (categoria.isBlank()) return null;
        // "Fiction / Romance / General" -> "Fiction"
        String principal = categoria.split("/")[0].trim();
        String traducido = CATEGORIAS.get(principal.toLowerCase(Locale.ROOT));
        if (traducido != null) return traducido;
        if (principal.equalsIgnoreCase("general")) return null;
        return recortar(principal, 80);
    }

    /** "Clean Code" + "A Handbook..." -> "Clean Code: A Handbook...", sin pasar de 200 letras. */
    private static String tituloCompleto(String titulo, String subtitulo) {
        if (titulo.isBlank()) return "";
        String completo = subtitulo.isBlank() ? titulo : titulo + ": " + subtitulo;
        return completo.length() <= 200 ? completo : recortar(titulo, 200);
    }

    /** Open Library a veces trae "Apellido, Nombre": se deja "Nombre Apellido". */
    private static String limpiarNombre(String nombre) {
        String n = nombre.replaceAll("\\s+", " ").trim();
        if (n.matches("[^,]+, [^,]+")) {
            String[] partes = n.split(", ");
            n = partes[1] + " " + partes[0];
        }
        return recortar(n, 150);
    }

    private static String recortar(String texto, int max) {
        String t = texto == null ? "" : texto.trim();
        return t.length() <= max ? t : t.substring(0, max).trim();
    }

    private static String texto(Object valor) {
        return valor == null ? "" : String.valueOf(valor).trim();
    }

    private static Integer numero(Object valor) {
        if (valor instanceof Number n) return n.intValue() > 0 ? n.intValue() : null;
        if (valor == null) return null;
        Matcher m = Pattern.compile("\\d+").matcher(String.valueOf(valor));
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    /** Saca el año de "1998", "1998-05-12", "March 1998" o "c1998". */
    private static Integer anioDe(String fecha) {
        if (fecha == null) return null;
        Matcher m = Pattern.compile("(1[5-9]\\d{2}|20\\d{2})").matcher(fecha);
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    /**
     * Lo que se sabe de un ISBN, todavia sin cruzar con la base de datos de la biblioteca.
     * generos: categorias de Google ya en español. temas: los subjects de Open Library (crudos).
     */
    public record FichaIsbn(String isbn, String titulo, List<String> autores, String editorial,
                            Integer anioPublicacion, Integer numPaginas, String urlPortada,
                            List<String> generos, List<String> temas, String fuente) {
        FichaIsbn conIsbn(String nuevo) {
            return new FichaIsbn(nuevo, titulo, List.copyOf(new LinkedHashSet<>(autores)), editorial,
                    anioPublicacion, numPaginas, urlPortada, generos, temas, fuente);
        }
    }
}
