package com.biblioteca.config;

import com.biblioteca.dto.EstadoAlmacenamientoDTO;
import com.biblioteca.dto.EstadoBucketDTO;
import com.biblioteca.service.AlmacenamientoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Al arrancar deja en el log si el almacenamiento de archivos (Cloudflare R2) esta listo.
 *
 * Es un diagnostico: si algo falla no se cae la aplicacion, solo avisa. Sirve para
 * detectar de un vistazo que faltan credenciales, que el bucket de PDF no existe o que
 * falta la URL publica de las portadas.
 */
@Component
public class AlmacenamientoVerificador {

    private static final Logger log = LoggerFactory.getLogger(AlmacenamientoVerificador.class);

    private final AlmacenamientoService almacenamientoService;

    public AlmacenamientoVerificador(AlmacenamientoService almacenamientoService) {
        this.almacenamientoService = almacenamientoService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void verificar() {
        try {
            EstadoAlmacenamientoDTO estado = almacenamientoService.estado();
            log.info("Almacenamiento R2 -> credenciales: {} | portadas: {} | pdfs: {} | portadas servidas por: {}",
                    estado.configurado() ? "OK" : "FALTAN",
                    resumen(estado.bucketPortadas()),
                    resumen(estado.bucketPdfs()),
                    estado.urlPublicaConfigurada() ? estado.urlPublica() : "el backend (/api/archivos/portadas)");
            if (!estado.configurado() || !estado.bucketPortadas().accesible() || !estado.bucketPdfs().accesible()) {
                log.warn("Revisa el .env y el panel de Cloudflare R2: portadas={} | pdfs={}",
                        estado.bucketPortadas().detalle(), estado.bucketPdfs().detalle());
            }
            if (estado.configurado() && !estado.urlPublicaConfigurada()) {
                log.info("Sin R2_URL_PUBLICO_PORTADAS: las portadas se ven igual, pero las sirve el backend. "
                        + "Para el VPS conviene activar la Public Development URL del bucket y ponerla en el .env");
            }
        } catch (RuntimeException e) {
            log.warn("No se pudo comprobar el almacenamiento de archivos: {}", e.getMessage());
        }
    }

    private static String resumen(EstadoBucketDTO bucket) {
        if (bucket.accesible()) return "LISTO (" + bucket.nombre() + ")";
        return "NO DISPONIBLE (" + bucket.nombre() + ")";
    }
}
