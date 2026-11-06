package com.biblioteca.service;

import com.biblioteca.config.AlmacenamientoProperties;
import com.biblioteca.dto.EstadoAlmacenamientoDTO;
import com.biblioteca.dto.EstadoBucketDTO;
import com.biblioteca.dto.PreparacionAlmacenamientoDTO;
import com.biblioteca.entity.TipoAccionAuditoria;
import com.biblioteca.exception.AlmacenamientoException;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketConfiguration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Guardar y servir los archivos de los libros en Cloudflare R2.
 *
 * Dos buckets con permisos distintos:
 *   - portadas: publico. La base de datos guarda el LINK de la imagen.
 *   - PDFs y comprobantes: privado. La base de datos guarda solo la CLAVE del objeto y
 *     el archivo se entrega con una URL firmada que caduca en 15 minutos.
 *
 * Las imagenes y los PDF nunca se guardan en la base de datos: solo su enlace o su clave.
 *
 * COMO SE VE UNA PORTADA EN EL NAVEGADOR (de mejor a peor):
 *   1. R2_URL_PUBLICO_PORTADAS (https://pub-xxxx.r2.dev): el navegador la pide directo a
 *      Cloudflare y el backend no gasta nada. Es lo recomendado para el VPS.
 *   2. R2_DOMINIO_PUBLICO: igual que la 1, con un dominio propio.
 *   3. Si no hay ninguna de las dos, el BACKEND sirve la imagen desde el bucket
 *      (GET /api/archivos/portadas/...), con cache de un año en el navegador. Asi la
 *      portada se ve aunque todavia no se haya activado la URL publica en Cloudflare.
 *
 * El link se "normaliza" al leerlo (urlVisible): si se guardo cuando no habia URL publica
 * y despues se configura, el catalogo pasa solo a la URL publica, sin tocar la base.
 *
 * Las claves de las portadas llevan version (portadas/{isbn}/{marca}.jpg): al cambiar la
 * portada cambia la URL, asi que el navegador no se queda con la imagen vieja en cache.
 */
@Service
public class AlmacenamientoService {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoService.class);

    /** Ruta del backend que sirve las portadas cuando no hay URL publica de R2. */
    public static final String RUTA_PORTADAS_BACKEND = "/api/archivos/portadas/";

    private static final String CARPETA_PORTADAS = "portadas/";
    private static final String CARPETA_PDFS = "pdfs/";
    private static final long MAX_IMAGEN_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PDF_BYTES = 50L * 1024 * 1024;

    private final S3Client s3;
    private final S3Presigner presigner;
    private final AlmacenamientoProperties props;
    private final AuditoriaService auditoriaService;

    public AlmacenamientoService(S3Client s3, S3Presigner presigner, AlmacenamientoProperties props,
                                 AuditoriaService auditoriaService) {
        this.s3 = s3;
        this.presigner = presigner;
        this.props = props;
        this.auditoriaService = auditoriaService;
    }

    // ===================== Portadas (bucket publico) =====================

    /**
     * Sube la portada que eligio el bibliotecario y devuelve el link para guardar en la base.
     * La portada anterior del mismo ISBN se borra solo si la transaccion se confirma.
     */
    public String subirPortada(MultipartFile archivo, String isbn) {
        exigirCredenciales();
        if (archivo == null || archivo.isEmpty()) {
            throw new OperacionInvalidaException("Debe elegir un archivo de imagen");
        }
        if (archivo.getSize() > MAX_IMAGEN_BYTES) {
            throw new OperacionInvalidaException("La portada no debe superar 5 MB");
        }
        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo leer el archivo de la portada", e);
        }
        return guardarPortada(datos, isbn);
    }

    /**
     * Sube una portada que ya venia descargada de internet (la del escaner de ISBN).
     * Mismo resultado que subirPortada(): deja una sola imagen por libro y devuelve su link.
     */
    public String subirPortadaDescargada(byte[] datos, String isbn) {
        exigirCredenciales();
        if (datos == null || datos.length == 0) {
            throw new OperacionInvalidaException("La portada descargada esta vacia");
        }
        if (datos.length > MAX_IMAGEN_BYTES) {
            throw new OperacionInvalidaException("La portada no debe superar 5 MB");
        }
        return guardarPortada(datos, isbn);
    }

    /**
     * Imagen de una portada, para servirla desde el backend (GET /api/archivos/portadas/...).
     * Solo lee del bucket de portadas y solo claves bajo "portadas/".
     */
    public ArchivoGuardado leerPortada(String ruta) {
        exigirCredenciales();
        if (ruta == null || !ruta.matches("[A-Za-z0-9_-]+(/[A-Za-z0-9_-]+)?\\.(jpg|png|webp)")) {
            throw new RecursoNoEncontradoException("La portada no existe");
        }
        String clave = CARPETA_PORTADAS + ruta;
        try {
            ResponseBytes<GetObjectResponse> objeto = s3.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucketDePortadas())
                    .key(clave)
                    .build());
            String tipo = objeto.response().contentType();
            return new ArchivoGuardado(objeto.asByteArray(),
                    tipo == null || tipo.isBlank() ? tipoDeExtension(clave) : tipo,
                    objeto.response().eTag(),
                    ruta.contains("/"));
        } catch (NoSuchKeyException e) {
            throw new RecursoNoEncontradoException("La portada no existe");
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                throw new RecursoNoEncontradoException("La portada no existe");
            }
            throw new AlmacenamientoException("No se pudo leer la portada del almacenamiento: " + e.getMessage(), e);
        } catch (SdkException e) {
            throw new AlmacenamientoException("No se pudo leer la portada del almacenamiento: " + e.getMessage(), e);
        }
    }

    /**
     * Link con el que el navegador ve la portada, a partir del que esta guardado en la base.
     *
     * Si el link es nuestro (de R2 o del backend), se rearma con la mejor base disponible
     * ahora mismo; si es de otro sitio, se devuelve tal cual. Arregla tambien los links que
     * se guardaron con la base "deducida" (https://bucket.cuenta.r2.dev), que nunca funciono.
     */
    public String urlVisible(String guardada) {
        if (guardada == null || guardada.isBlank()) return null;
        String clave = claveDeUrl(guardada.trim());
        return clave == null ? guardada : linkDe(clave);
    }

    /**
     * Borra la portada vieja de un libro, pero solo si la transaccion se confirma.
     * Se usa al reemplazar o quitar la portada (tambien si cambio el ISBN del libro).
     */
    public void eliminarPortadaAlConfirmar(String urlGuardada) {
        String clave = claveDeUrl(urlGuardada);
        if (clave == null || !tieneCredenciales()) return;
        String bucket = bucketDePortadas();
        alConfirmar(() -> borrarSilencioso(bucket, clave));
    }

    /** Borra la portada de un libro a partir de su link (si es nuestro). */
    public void eliminarPortada(String urlGuardada) {
        String clave = claveDeUrl(urlGuardada);
        if (clave == null) return;
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucketDePortadas()).key(clave).build());
        } catch (SdkException e) {
            throw new AlmacenamientoException("No se pudo eliminar la portada del almacenamiento: " + e.getMessage(), e);
        }
    }

    // ===================== PDFs (bucket privado) =====================

    /**
     * Sube el PDF de un libro virtual y devuelve la clave del objeto, NO una URL.
     * Cada subida tiene su propia clave: si la transaccion se revierte, el PDF nuevo se borra
     * y el anterior sigue intacto. El anterior se borra con eliminarPdfAlConfirmar().
     */
    public String subirPdf(MultipartFile archivo, String isbn) {
        exigirCredenciales();
        if (archivo == null || archivo.isEmpty()) {
            throw new OperacionInvalidaException("Debe elegir un archivo PDF");
        }
        if (archivo.getSize() > MAX_PDF_BYTES) {
            throw new OperacionInvalidaException("El PDF no debe superar 50 MB");
        }
        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo leer el archivo del PDF", e);
        }
        // Se mira el contenido, no la extension: un .pdf renombrado no pasa.
        if (datos.length < 5 || !(datos[0] == '%' && datos[1] == 'P' && datos[2] == 'D' && datos[3] == 'F')) {
            throw new OperacionInvalidaException("El archivo no es un PDF valido");
        }
        String clave = CARPETA_PDFS + carpetaDeIsbn(isbn) + "/" + System.currentTimeMillis() + ".pdf";
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucketDePdfs())
                            .key(clave)
                            .contentType("application/pdf")
                            .build(),
                    RequestBody.fromBytes(datos));
        } catch (SdkException e) {
            throw new AlmacenamientoException("No se pudo guardar el PDF en el almacenamiento: " + e.getMessage(), e);
        }
        alRevertir(() -> borrarSilencioso(bucketDePdfs(), clave));
        return clave;
    }

    /** Borra un PDF viejo, pero solo si la transaccion actual se confirma. */
    public void eliminarPdfAlConfirmar(String claveAnterior) {
        if (claveAnterior == null || claveAnterior.isBlank()) return;
        alConfirmar(() -> borrarSilencioso(bucketDePdfs(), claveAnterior));
    }

    /**
     * URL temporal del PDF. Caduca sola, asi que el link no se puede reenviar.
     *
     * @param nombreArchivo nombre con el que el navegador guarda el PDF.
     * @param descarga      true: el navegador lo descarga (compra). false: lo abre para leerlo.
     */
    public String urlFirmadaPdf(String clave, Duration duracion, String nombreArchivo, boolean descarga) {
        if (clave == null || clave.isBlank()) {
            throw new OperacionInvalidaException("Este libro no tiene un PDF cargado");
        }
        exigirCredenciales();
        String disposicion = (descarga ? "attachment" : "inline") + "; " + nombreParaCabecera(nombreArchivo);
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(duracion)
                            .getObjectRequest(builder -> builder
                                    .bucket(bucketDePdfs())
                                    .key(clave)
                                    .responseContentType("application/pdf")
                                    .responseContentDisposition(disposicion))
                            .build())
                    .url().toString();
        } catch (SdkException e) {
            throw new AlmacenamientoException("No se pudo generar el enlace del PDF: " + e.getMessage(), e);
        }
    }

    /** Borra el PDF de un libro. Si no hay nada que borrar, no hace nada. */
    public void eliminarPdf(String clave) {
        if (clave == null || clave.isBlank()) return;
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucketDePdfs()).key(clave).build());
        } catch (SdkException e) {
            throw new AlmacenamientoException("No se pudo eliminar el PDF del almacenamiento: " + e.getMessage(), e);
        }
    }

    // ===================== Comprobantes de Yape (bucket privado) =====================

    /**
     * Sube la captura del comprobante de Yape y devuelve la clave del objeto, NO una URL.
     * Va al bucket privado (el de los PDF): solo el personal la ve, con una URL firmada.
     */
    public String subirComprobante(MultipartFile archivo, Long idComprobante) {
        exigirCredenciales();
        if (archivo == null || archivo.isEmpty()) {
            throw new OperacionInvalidaException("Debe elegir la captura del pago");
        }
        if (archivo.getSize() > MAX_IMAGEN_BYTES) {
            throw new OperacionInvalidaException("La captura no debe superar 5 MB");
        }
        byte[] datos;
        try {
            datos = archivo.getBytes();
        } catch (IOException e) {
            throw new AlmacenamientoException("No se pudo leer la imagen del comprobante", e);
        }
        String ext = extensionDeImagen(datos);
        String clave = "comprobantes/" + idComprobante + "." + ext;
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucketDePdfs())
                            .key(clave)
                            .contentType(tipoDeExtension(clave))
                            .build(),
                    RequestBody.fromBytes(datos));
        } catch (SdkException e) {
            throw new AlmacenamientoException("No se pudo guardar el comprobante en el almacenamiento: " + e.getMessage(), e);
        }
        return clave;
    }

    /** URL temporal de la captura del comprobante. Caduca sola, como la del PDF. */
    public String urlFirmadaComprobante(String clave, Duration duracion) {
        if (clave == null || clave.isBlank()) {
            throw new OperacionInvalidaException("Este comprobante no tiene imagen");
        }
        exigirCredenciales();
        try {
            return presigner.presignGetObject(GetObjectPresignRequest.builder()
                            .signatureDuration(duracion)
                            .getObjectRequest(builder -> builder.bucket(bucketDePdfs()).key(clave))
                            .build())
                    .url().toString();
        } catch (SdkException e) {
            throw new AlmacenamientoException("No se pudo generar el enlace del comprobante: " + e.getMessage(), e);
        }
    }

    // ===================== Panel de administracion =====================

    /**
     * Comprueba que el token de R2 puede ESCRIBIR, no solo leer.
     * Sube un archivo vacio de prueba. Solo se llama cuando alguien lo pide desde el panel.
     */
    public boolean puedeEscribir() {
        if (!tieneCredenciales()) return false;
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucketDePortadas())
                            .key("_pruebas/permiso-de-escritura")
                            .contentType("text/plain")
                            .build(),
                    RequestBody.fromBytes(new byte[0]));
            return true;
        } catch (SdkException e) {
            log.warn("El token de R2 no puede escribir en el bucket de portadas: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Prepara el almacenamiento: crea en Cloudflare R2 los buckets que falten
     * (el de portadas y el privado de los PDF y comprobantes).
     */
    @Transactional
    public PreparacionAlmacenamientoDTO preparar(Long idActor) {
        exigirCredenciales();
        List<String> creados = new ArrayList<>();
        for (String nombre : List.of(bucketDePortadas(), bucketDePdfs())) {
            if (revisarBucket(nombre).existe()) {
                continue;
            }
            if (crearBucket(nombre)) {
                creados.add(nombre);
            }
        }
        auditoriaService.registrar(idActor, TipoAccionAuditoria.PREPARAR_ALMACENAMIENTO, "ALMACENAMIENTO", null,
                creados.isEmpty()
                        ? "Revisó el almacenamiento de archivos (no faltaba ningún bucket)"
                        : "Preparó el almacenamiento de archivos (creó el bucket " + String.join(", ", creados) + ")");
        return new PreparacionAlmacenamientoDTO(creados, estado());
    }

    /**
     * Diagnostico para el panel: si las credenciales sirven, si los buckets existen y
     * como se estan sirviendo las portadas. Nunca lanza excepcion: informa del problema.
     */
    @Transactional(readOnly = true)
    public EstadoAlmacenamientoDTO estado() {
        boolean configurado = tieneCredenciales();
        String basePublica = basePublicaDePortadas();
        boolean publica = basePublica != null;
        String base = publica ? basePublica : RUTA_PORTADAS_BACKEND + "...";
        String origen = origenDeBasePublica();
        if (!configurado) {
            String aviso = "Faltan las credenciales de R2 en el archivo .env (R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, "
                    + "R2_SECRET_ACCESS_KEY)";
            return new EstadoAlmacenamientoDTO(false, publica, base, origen,
                    new EstadoBucketDTO(valorO(props.getBucketPortadas()), false, false, aviso),
                    new EstadoBucketDTO(valorO(props.getBucketPdfs()), false, false, aviso));
        }
        return new EstadoAlmacenamientoDTO(true, publica, base, origen,
                revisarBucket(props.getBucketPortadas()),
                revisarBucket(props.getBucketPdfs()));
    }

    // ===================== Internos =====================

    /** Sube la imagen con una clave nueva y, al confirmar, borra las anteriores del mismo ISBN. */
    private String guardarPortada(byte[] datos, String isbn) {
        String ext = extensionDeImagen(datos);
        String carpeta = carpetaDeIsbn(isbn);
        String clave = CARPETA_PORTADAS + carpeta + "/" + System.currentTimeMillis() + "." + ext;
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucketDePortadas())
                            .key(clave)
                            .contentType(tipoDeExtension(clave))
                            // La clave cambia con cada portada nueva: se puede cachear un año.
                            .cacheControl("public, max-age=31536000, immutable")
                            .build(),
                    RequestBody.fromBytes(datos));
        } catch (SdkException e) {
            throw new AlmacenamientoException("No se pudo guardar la portada en el almacenamiento: " + e.getMessage(), e);
        }
        String bucket = bucketDePortadas();
        // Si la transaccion se revierte, la portada nueva sobra; si se confirma, sobran las viejas.
        alRevertir(() -> borrarSilencioso(bucket, clave));
        alConfirmar(() -> {
            borrarConPrefijoExcepto(bucket, CARPETA_PORTADAS + carpeta + "/", clave);
            // Formato antiguo: portadas/{isbn}.jpg (sin carpeta ni version).
            borrarConPrefijoExcepto(bucket, CARPETA_PORTADAS + carpeta + ".", clave);
        });
        return linkDe(clave);
    }

    /** Link de una clave del bucket de portadas: la URL publica si existe; si no, la ruta del backend. */
    private String linkDe(String clave) {
        String base = basePublicaDePortadas();
        if (base != null) {
            return base + "/" + clave;
        }
        return RUTA_PORTADAS_BACKEND + clave.substring(CARPETA_PORTADAS.length());
    }

    /**
     * Clave del objeto a partir de un link guardado, o null si el link no es nuestro.
     * Reconoce la URL publica, el dominio propio, la ruta del backend y la base "deducida"
     * que usaban las versiones anteriores.
     */
    private String claveDeUrl(String url) {
        if (url == null || url.isBlank()) return null;
        String sinConsulta = url.split("[?#]", 2)[0];
        if (sinConsulta.startsWith(RUTA_PORTADAS_BACKEND)) {
            return CARPETA_PORTADAS + sinConsulta.substring(RUTA_PORTADAS_BACKEND.length());
        }
        // Tambien con el host del backend delante (http://IP:8080/api/archivos/portadas/...).
        int i = sinConsulta.indexOf(RUTA_PORTADAS_BACKEND);
        if (i > 0 && sinConsulta.startsWith("http")) {
            return CARPETA_PORTADAS + sinConsulta.substring(i + RUTA_PORTADAS_BACKEND.length());
        }
        for (String base : basesConocidas()) {
            if (sinConsulta.startsWith(base + "/" + CARPETA_PORTADAS)) {
                return sinConsulta.substring(base.length() + 1);
            }
        }
        return null;
    }

    /** Todas las bases con las que se pudo haber guardado un link de portada. */
    private List<String> basesConocidas() {
        List<String> bases = new ArrayList<>();
        String publica = basePublicaDePortadas();
        if (publica != null) bases.add(publica);
        String dominio = baseDeDominio();
        if (dominio != null && !bases.contains(dominio)) bases.add(dominio);
        String bucket = props.getBucketPortadas();
        String cuenta = props.getAccountId();
        if (bucket != null && !bucket.isBlank() && cuenta != null && !cuenta.isBlank()) {
            // La base "deducida" de versiones anteriores. Nunca sirvio en el navegador.
            bases.add("https://" + bucket.trim() + "." + cuenta.trim() + ".r2.dev");
        }
        return bases;
    }

    /**
     * Base publica de las portadas (sin barra final) o null si no hay ninguna.
     *   1. R2_URL_PUBLICO_PORTADAS (Public Development URL, https://pub-xxxx.r2.dev)
     *   2. R2_DOMINIO_PUBLICO (dominio propio)
     * El hash de pub-xxxx.r2.dev NO se puede deducir de la cuenta: si no esta en el .env,
     * las portadas las sirve el backend.
     */
    private String basePublicaDePortadas() {
        String delEnv = props.getUrlPublicoPortadas();
        if (delEnv != null && !delEnv.isBlank()) {
            String base = delEnv.trim().replaceAll("/+$", "");
            return base.startsWith("http") ? base : "https://" + base;
        }
        return baseDeDominio();
    }

    private String baseDeDominio() {
        String dominio = props.getDominioPublico();
        if (dominio == null || dominio.isBlank()) return null;
        return "https://" + dominio.trim().replaceFirst("^https?://", "").replaceAll("/+$", "");
    }

    /** De donde sale la base de las portadas: para que el panel lo diga claro. */
    private String origenDeBasePublica() {
        if (props.getUrlPublicoPortadas() != null && !props.getUrlPublicoPortadas().isBlank()) {
            return "R2_URL_PUBLICO_PORTADAS";
        }
        if (props.getDominioPublico() != null && !props.getDominioPublico().isBlank()) {
            return "R2_DOMINIO_PUBLICO";
        }
        return "el backend (falta la URL publica de R2)";
    }

    /** Comprueba si un bucket existe y si las credenciales pueden usarlo. */
    private EstadoBucketDTO revisarBucket(String nombre) {
        if (nombre == null || nombre.isBlank()) {
            return new EstadoBucketDTO("", false, false, "No se ha configurado el nombre del bucket");
        }
        try {
            s3.headBucket(HeadBucketRequest.builder().bucket(nombre).build());
            return new EstadoBucketDTO(nombre, true, true, "Bucket listo");
        } catch (S3Exception e) {
            int codigo = e.statusCode();
            if (codigo == 404) {
                return new EstadoBucketDTO(nombre, false, false,
                        "El bucket no existe en la cuenta de Cloudflare R2: crealo con el boton "
                                + "'Preparar almacenamiento' o desde el panel de R2");
            }
            if (codigo == 403) {
                return new EstadoBucketDTO(nombre, true, false,
                        "El bucket existe pero el token de R2 no tiene permiso sobre el");
            }
            return new EstadoBucketDTO(nombre, false, false, "R2 respondio con el codigo " + codigo);
        } catch (SdkException e) {
            return new EstadoBucketDTO(nombre, false, false, "No se pudo consultar el bucket: " + e.getMessage());
        }
    }

    private boolean tieneCredenciales() {
        return props.getAccountId() != null && !props.getAccountId().isBlank()
                && props.getAccessKeyId() != null && !props.getAccessKeyId().isBlank()
                && props.getSecretAccessKey() != null && !props.getSecretAccessKey().isBlank();
    }

    /** Hay credenciales de R2: el servicio de libros lo mira antes de intentar bajar una portada. */
    public boolean configurado() {
        return tieneCredenciales();
    }

    /**
     * Crea un bucket en R2. Devuelve true si lo creo ahora y false si ya existia.
     * Se intenta primero sin ubicacion y, si R2 la pide, con la restriccion "auto".
     */
    private boolean crearBucket(String nombre) {
        try {
            s3.createBucket(CreateBucketRequest.builder().bucket(nombre).build());
            log.info("Bucket de R2 creado: {}", nombre);
            return true;
        } catch (S3Exception e) {
            if (e.statusCode() == 409) {
                return false; // ya existia
            }
            if (e.statusCode() == 400 && elErrorHablaDeUbicacion(e)) {
                try {
                    s3.createBucket(CreateBucketRequest.builder()
                            .bucket(nombre)
                            .createBucketConfiguration(CreateBucketConfiguration.builder()
                                    .locationConstraint("auto")
                                    .build())
                            .build());
                    log.info("Bucket de R2 creado: {}", nombre);
                    return true;
                } catch (S3Exception reintento) {
                    throw new OperacionInvalidaException(
                            "No se pudo crear el bucket '" + nombre + "' en Cloudflare R2 (codigo "
                                    + reintento.statusCode() + "). Crealo en el panel de Cloudflare "
                                    + "(R2 > Create bucket) con ese nombre");
                }
            }
            if (e.statusCode() == 403) {
                throw new OperacionInvalidaException(
                        "El token de R2 no tiene permiso para crear buckets. Crealo en el panel de Cloudflare "
                                + "(R2 > Create bucket) con el nombre '" + nombre + "' y vuelve a intentarlo");
            }
            throw new OperacionInvalidaException(
                    "No se pudo crear el bucket '" + nombre + "' en Cloudflare R2 (codigo " + e.statusCode()
                            + "). Crealo en el panel de Cloudflare (R2 > Create bucket) con ese nombre");
        } catch (SdkException e) {
            // No se pudo ni hablar con R2 (cuenta mal escrita, sin red...): mensaje claro, no un 500 seco.
            throw new OperacionInvalidaException(
                    "No se pudo crear el bucket '" + nombre + "' en Cloudflare R2: " + e.getMessage());
        }
    }

    private static boolean elErrorHablaDeUbicacion(S3Exception e) {
        String mensaje = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);
        return mensaje.contains("location") || mensaje.contains("ubicacion") || mensaje.contains("locationconstraint");
    }

    private static String valorO(String v) {
        return v == null ? "" : v;
    }

    /** Sin credenciales no se puede subir nada: se avisa con un mensaje util, no con un 500. */
    private void exigirCredenciales() {
        if (!tieneCredenciales()) {
            throw new OperacionInvalidaException(
                    "El almacenamiento de archivos no esta configurado. Revisa las credenciales de Cloudflare R2 "
                            + "(R2_ACCOUNT_ID, R2_ACCESS_KEY_ID, R2_SECRET_ACCESS_KEY) en el archivo .env");
        }
    }

    /** Ejecuta la accion cuando la transaccion se confirma (o ya, si no hay transaccion). */
    private static void alConfirmar(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    accion.run();
                }
            });
        } else {
            accion.run();
        }
    }

    /** Ejecuta la accion solo si la transaccion se revierte. */
    private static void alRevertir(Runnable accion) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int estado) {
                    if (estado == STATUS_ROLLED_BACK) {
                        accion.run();
                    }
                }
            });
        }
    }

    /**
     * Limpieza de archivos sobrantes DESPUES de confirmar. La operacion ya esta guardada:
     * si la limpieza falla solo queda un archivo huerfano en R2, asi que se deja en el log
     * (no hay a quien devolverle el error).
     */
    private void borrarSilencioso(String bucket, String clave) {
        try {
            s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(clave).build());
        } catch (SdkException e) {
            log.warn("No se pudo borrar el archivo sobrante {} de R2: {}", clave, e.getMessage());
        }
    }

    private void borrarConPrefijoExcepto(String bucket, String prefijo, String conservar) {
        try {
            ListObjectsV2Response respuesta = s3.listObjectsV2(ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .prefix(prefijo)
                    .build());
            for (S3Object objeto : respuesta.contents()) {
                if (!objeto.key().equals(conservar)) {
                    s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(objeto.key()).build());
                }
            }
        } catch (SdkException e) {
            log.warn("No se pudo limpiar las portadas anteriores ({}) en R2: {}", prefijo, e.getMessage());
        }
    }

    private String bucketDePortadas() {
        String b = props.getBucketPortadas();
        if (b == null || b.isBlank()) {
            throw new OperacionInvalidaException("No se ha configurado el bucket de portadas (R2_BUCKET_PORTADAS)");
        }
        return b.trim();
    }

    private String bucketDePdfs() {
        String b = props.getBucketPdfs();
        if (b == null || b.isBlank()) {
            throw new OperacionInvalidaException("No se ha configurado el bucket de PDFs (R2_BUCKET_PDFS)");
        }
        return b.trim();
    }

    /** El ISBN como nombre de carpeta: solo digitos y X (nada que rompa la clave). */
    private static String carpetaDeIsbn(String isbn) {
        String limpio = isbn == null ? "" : isbn.replaceAll("[^0-9Xx]", "").toUpperCase(Locale.ROOT);
        return limpio.isEmpty() ? "sin-isbn" : limpio;
    }

    /**
     * Tipo de imagen mirando los primeros bytes (no el nombre ni lo que diga el navegador).
     * Solo se aceptan JPG, PNG y WEBP.
     */
    static String extensionDeImagen(byte[] d) {
        if (d.length >= 3 && (d[0] & 0xFF) == 0xFF && (d[1] & 0xFF) == 0xD8 && (d[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (d.length >= 8 && (d[0] & 0xFF) == 0x89 && d[1] == 'P' && d[2] == 'N' && d[3] == 'G') {
            return "png";
        }
        if (d.length >= 12 && d[0] == 'R' && d[1] == 'I' && d[2] == 'F' && d[3] == 'F'
                && d[8] == 'W' && d[9] == 'E' && d[10] == 'B' && d[11] == 'P') {
            return "webp";
        }
        throw new OperacionInvalidaException("La imagen debe ser JPG, PNG o WEBP");
    }

    private static String tipoDeExtension(String clave) {
        if (clave.endsWith(".png")) return "image/png";
        if (clave.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    /** filename="..." (ASCII) y filename*=UTF-8''... para que las tildes del titulo se vean bien. */
    private static String nombreParaCabecera(String nombre) {
        String base = (nombre == null || nombre.isBlank()) ? "libro" : nombre.trim();
        String ascii = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9 ._-]", "")
                .trim();
        if (ascii.isEmpty()) ascii = "libro";
        if (ascii.length() > 80) ascii = ascii.substring(0, 80).trim();
        String utf8 = URLEncoder.encode(base + ".pdf", StandardCharsets.UTF_8).replace("+", "%20");
        return "filename=\"" + ascii + ".pdf\"; filename*=UTF-8''" + utf8;
    }

    /**
     * Archivo leido del bucket, listo para que el controlador lo sirva.
     * versionado: la clave lleva marca de tiempo, asi que el navegador lo puede cachear un año.
     */
    public record ArchivoGuardado(byte[] datos, String tipoContenido, String etag, boolean versionado) {}
}
