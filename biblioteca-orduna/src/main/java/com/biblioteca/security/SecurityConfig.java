package com.biblioteca.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Configuración de seguridad: API sin sesión (stateless) con JWT, CORS global y
 * permisos por rol. Toda la matriz de permisos está aquí, en un solo lugar.
 *
 * Matriz de permisos (roles del documento: LECTOR, BIBLIOTECARIO, GERENTE):
 *   Público .......................... GET /api/salud, GET /api/eventos (SSE; el token se valida
 *                                      dentro del controlador), el frontend compilado (todo GET fuera de
 *                                      /api/), login, registro de lector, GET catálogo (libros, autores,
 *                                      géneros), GET /api/configuracion (nombre/horario), el QR
 *                                      de Yape y las portadas servidas por el backend
 *                                      (GET /api/archivos/portadas/**, imágenes públicas)
 *   GERENTE .......................... PUT /api/configuracion, historial de configuración,
 *                                      GET /api/auditoria/** (quién hizo qué),
 *                                      POST /api/notificaciones/generar
 *   LECTOR ........................... POST /api/reservas, POST /api/accesos-virtuales
 *   Cualquier usuario logueado ....... /api/mi/** (mis préstamos, multas, reservas, panel),
 *                                      /api/usuarios/me, mis notificaciones, cancelar mi reserva,
 *                                      mis libros virtuales (leer, subir comprobante, cancelar)
 *   BIBLIOTECARIO o GERENTE .......... todo lo demás de gestión: catálogo (escritura), ejemplares,
 *                                      préstamos, devoluciones, multas, reservas (listado/cola),
 *                                      usuarios, sanciones, turnos, reportes, cierre diario,
 *                                      dashboard, crear avisos, libros virtuales (escritura),
 *                                      comprobantes de Yape (/api/comprobantes/**) y la caja
 *                                      (/api/cajas/**)
 */
@Configuration
public class SecurityConfig {

    private static final String LECTOR = "LECTOR";
    private static final String BIBLIOTECARIO = "BIBLIOTECARIO";
    private static final String GERENTE = "GERENTE";

    private final JwtService jwtService;
    private final SecurityErrorHandler errorHandler;

    @Value("${cors.allowed-origins}")
    private String origenesPermitidos;

    public SecurityConfig(JwtService jwtService, SecurityErrorHandler errorHandler) {
        this.jwtService = jwtService;
        this.errorHandler = errorHandler;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)            // API REST con JWT: no usa cookies de sesión
                .cors(Customizer.withDefaults())                  // usa corsConfigurationSource()
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorHandler)  // 401
                        .accessDeniedHandler(errorHandler))      // 403
                .authorizeHttpRequests(auth -> auth
                        // ======== Público ========
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/error").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/salud").permitAll()
                        // Canal SSE: valida el JWT dentro del controlador porque EventSource no
                        // puede enviar la cabecera Authorization (manda ?token=).
                        .requestMatchers(HttpMethod.GET, "/api/eventos").permitAll()
                        // Frontend compilado (index.html, /assets/*, y rutas de React como /libros):
                        // todo GET que NO sea de la API es publico. La API sigue protegida abajo.
                        .requestMatchers((RequestMatcher) req -> HttpMethod.GET.matches(req.getMethod())
                                && !req.getRequestURI().startsWith("/api/")).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/usuarios/registro").permitAll()
                        // QR de Yape Negocio: es una imagen publica (la mete un <img> sin token).
                        .requestMatchers(HttpMethod.GET, "/api/pagos/yape/qr").permitAll()
                        // Portadas servidas por el backend (cuando no hay URL publica de R2):
                        // las pide un <img> sin token y el bucket de portadas ya es publico.
                        .requestMatchers(HttpMethod.GET, "/api/archivos/portadas/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/libros/**", "/api/autores/**", "/api/generos/**",
                                "/api/configuracion").permitAll()

                        // ======== Solo GERENTE ========
                        // GET /api/configuracion (ruta exacta) es publica y ya quedo permitida arriba.
                        // Todo lo demas bajo ese prefijo es solo del gerente: se protege el prefijo
                        // entero para que un endpoint nuevo no quede abierto por descuido.
                        .requestMatchers("/api/configuracion/**").hasRole(GERENTE)
                        // Auditoria: quien hizo que. Solo el gerente.
                        .requestMatchers("/api/auditoria/**").hasRole(GERENTE)
                        .requestMatchers(HttpMethod.POST, "/api/notificaciones/generar").hasRole(GERENTE)
                        // Almacenamiento: crear en R2 los buckets que falten. Solo el gerente.
                        // Va aqui (antes que el bloque de bibliotecario/gerente) porque el
                        // orden de las reglas es posicional: el primero que coincide gana.
                        .requestMatchers(HttpMethod.POST, "/api/almacenamiento/preparar").hasRole(GERENTE)

                        // ======== Solo LECTOR ========
                        .requestMatchers(HttpMethod.POST, "/api/reservas").hasRole(LECTOR)
                        .requestMatchers(HttpMethod.POST, "/api/accesos-virtuales").hasRole(LECTOR)

                        // ======== Cualquier usuario logueado (datos propios) ========
                        .requestMatchers("/api/mi/**", "/api/usuarios/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/notificaciones", "/api/notificaciones/no-leidas/total")
                        .authenticated()
                        .requestMatchers(HttpMethod.PATCH, "/api/notificaciones/*/leida", "/api/notificaciones/leidas",
                                "/api/reservas/*/cancelar").authenticated()
                        // Libros virtuales: los suyos (leer, subir el comprobante, cancelar).
                        .requestMatchers(HttpMethod.GET, "/api/accesos-virtuales/mis-libros",
                                "/api/accesos-virtuales/*/pdf",
                                "/api/accesos-virtuales/*/comprobantes").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/accesos-virtuales/*/comprobante",
                                "/api/accesos-virtuales/*/cancelar").authenticated()

                        // ======== BIBLIOTECARIO o GERENTE ========
                        .requestMatchers("/api/libros/**", "/api/autores/**", "/api/generos/**", "/api/ejemplares/**",
                                "/api/prestamos/**", "/api/devoluciones/**", "/api/multas/**", "/api/reservas/**",
                                "/api/usuarios/**", "/api/sanciones/**", "/api/turnos/**", "/api/reportes/**",
                                "/api/cierre-diario/**", "/api/dashboard/**", "/api/notificaciones/**",
                                "/api/almacenamiento/**", "/api/comprobantes/**", "/api/cajas/**")
                        .hasAnyRole(BIBLIOTECARIO, GERENTE)
                        // Panel del mostrador de libros virtuales (pedidos, codigo del cliente, cobrar).
                        .requestMatchers(HttpMethod.GET, "/api/accesos-virtuales/pedidos",
                                "/api/accesos-virtuales/codigo/*", "/api/accesos-virtuales/libro/*")
                        .hasAnyRole(BIBLIOTECARIO, GERENTE)
                        .requestMatchers(HttpMethod.POST, "/api/accesos-virtuales/*/cambiar",
                                "/api/accesos-virtuales/*/cobrar")
                        .hasAnyRole(BIBLIOTECARIO, GERENTE)

                        // ======== Resto: basta con estar logueado ========
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtFilter(jwtService), UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /** CORS global: permite que el frontend (otro puerto/dominio) consuma la API. */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(Arrays.stream(origenesPermitidos.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).toList());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        // Para que el frontend pueda leer el nombre del archivo CSV descargado
        config.setExposedHeaders(List.of(HttpHeaders.CONTENT_DISPOSITION));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    /** Encriptador de contraseñas (BCrypt). Lo usan AuthService (login) y UsuarioService (registro). */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
