package com.biblioteca.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Cliente S3 apuntando a Cloudflare R2.
 *
 * R2 habla el protocolo S3, pero con dos diferencias frente a AWS:
 *   - la region es "auto", no "us-east-1";
 *   - hay que forzar el path style, porque el host lleva el id de cuenta
 *     y no el nombre del bucket.
 *
 * Si faltan credenciales se usan valores de relleno para que la aplicacion
 * arranque igual: el servicio de almacenamiento avisa del problema al usarse.
 */
@Configuration
public class AlmacenamientoConfig {

    private static final String RELLENO = "sin-configurar";

    @Bean
    public S3Client s3Client(AlmacenamientoProperties props) {
        return S3Client.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create("https://" + valor(props.getAccountId()) + ".r2.cloudflarestorage.com"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(valor(props.getAccessKeyId()), valor(props.getSecretAccessKey()))))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /** Firma URLs temporales de descarga: asi el PDF privado no se puede compartir por su link. */
    @Bean
    public S3Presigner s3Presigner(AlmacenamientoProperties props) {
        return S3Presigner.builder()
                .region(Region.of("auto"))
                .endpointOverride(URI.create("https://" + valor(props.getAccountId()) + ".r2.cloudflarestorage.com"))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(valor(props.getAccessKeyId()), valor(props.getSecretAccessKey()))))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    private static String valor(String v) {
        return v == null || v.isBlank() ? RELLENO : v.trim();
    }
}
