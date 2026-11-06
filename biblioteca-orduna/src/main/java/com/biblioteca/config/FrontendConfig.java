package com.biblioteca.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.time.Duration;

/**
 * Sirve el frontend (React ya compilado) desde el mismo backend, en produccion.
 *
 * La imagen de Docker copia frontend/dist dentro de src/main/resources/static antes de
 * empaquetar el jar: asi el sistema entero es UN solo contenedor, en un solo puerto, y
 * el navegador habla con la API en el mismo origen (sin CORS).
 *
 *   /assets/**  -> archivos con hash en el nombre: cache de un año.
 *   /api/**     -> los controladores (nunca pasa por aqui; si la ruta no existe, 404 JSON).
 *   cualquier otra ruta sin archivo (/libros, /prestamos...) -> index.html, para que
 *                  React Router la resuelva (si no, recargar la pagina daria 404).
 *
 * En desarrollo (mvn spring-boot:run sin el frontend copiado) no hay index.html y todo
 * sigue igual: el frontend corre aparte con Vite en el 5173.
 */
@Configuration
public class FrontendConfig implements WebMvcConfigurer {

    private static final String UBICACION = "classpath:/static/";

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/assets/**")
                .addResourceLocations(UBICACION + "assets/")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());

        registry.addResourceHandler("/**")
                .addResourceLocations(UBICACION)
                .setCacheControl(CacheControl.noCache())
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String ruta, Resource ubicacion) throws IOException {
                        if (!ruta.isEmpty() && !ruta.endsWith("/")) {
                            Resource archivo = ubicacion.createRelative(ruta);
                            if (archivo.exists() && archivo.isReadable()) {
                                return archivo;
                            }
                        }
                        // Una ruta de la API que no existe NO debe devolver la pagina: 404 JSON.
                        if (ruta.startsWith("api/") || ruta.equals("api")) {
                            return null;
                        }
                        Resource index = new ClassPathResource("/static/index.html");
                        return index.exists() ? index : null;
                    }
                });
    }
}
