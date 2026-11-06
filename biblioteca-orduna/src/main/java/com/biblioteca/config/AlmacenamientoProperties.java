package com.biblioteca.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credenciales y nombres de los buckets de Cloudflare R2.
 *
 * Los valores vienen del archivo .env (nunca versionado). Si faltan, el servicio
 * de almacenamiento responde con un error claro en vez de fallar al arrancar.
 */
@ConfigurationProperties(prefix = "r2")
public class AlmacenamientoProperties {

    /** ID de la cuenta de Cloudflare. Arma el endpoint S3: https://{accountId}.r2.cloudflarestorage.com */
    private String accountId = "";

    /** Credencial publica del token de R2. */
    private String accessKeyId = "";

    /** Credencial privada del token de R2. */
    private String secretAccessKey = "";

    /** Bucket publico: portadas de los libros. */
    private String bucketPortadas = "";

    /** Bucket privado: PDFs de los libros virtuales (se sirven con URL firmada). */
    private String bucketPdfs = "";

    /** Base publica de las portadas, sin barra final. Ej: https://pub-abc123.r2.dev */
    private String urlPublicoPortadas = "";

    /**
     * Dominio publico propio del bucket de portadas (opcional).
     * Ej: portadas.mibiblioteca.pe -> las imagenes quedan en https://portadas.mibiblioteca.pe
     */
    private String dominioPublico = "";

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }
    public String getAccessKeyId() { return accessKeyId; }
    public void setAccessKeyId(String accessKeyId) { this.accessKeyId = accessKeyId; }
    public String getSecretAccessKey() { return secretAccessKey; }
    public void setSecretAccessKey(String secretAccessKey) { this.secretAccessKey = secretAccessKey; }
    public String getBucketPortadas() { return bucketPortadas; }
    public void setBucketPortadas(String bucketPortadas) { this.bucketPortadas = bucketPortadas; }
    public String getBucketPdfs() { return bucketPdfs; }
    public void setBucketPdfs(String bucketPdfs) { this.bucketPdfs = bucketPdfs; }
    public String getUrlPublicoPortadas() { return urlPublicoPortadas; }
    public void setUrlPublicoPortadas(String urlPublicoPortadas) { this.urlPublicoPortadas = urlPublicoPortadas; }
    public String getDominioPublico() { return dominioPublico; }
    public void setDominioPublico(String dominioPublico) { this.dominioPublico = dominioPublico; }
}
