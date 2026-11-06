package com.biblioteca.service;

import com.biblioteca.dto.LoginDTO;
import com.biblioteca.dto.LoginResponseDTO;
import com.biblioteca.entity.Rol;
import com.biblioteca.entity.Usuario;
import com.biblioteca.exception.CredencialesInvalidasException;
import com.biblioteca.repository.UsuarioRepository;
import com.biblioteca.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF05 - Autenticación. La lógica vive aquí y no en el controlador.
 *
 * Orden de las comprobaciones:
 *  1. Credenciales válidas (mismo mensaje si el email no existe o la clave está mal,
 *     para no revelar qué correos están registrados).
 *  2. Cuenta activa.
 *  3. Sin sanción vigente, solo si es lector (lanza UsuarioSancionadoException -> 403).
 *
 * El token solo se emite si las tres comprobaciones pasan.
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SancionService sancionService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, SancionService sancionService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.sancionService = sancionService;
    }

    @Transactional(readOnly = true)
    public LoginResponseDTO login(LoginDTO datos) {
        Usuario usuario = usuarioRepository.findByEmailIgnoreCase(datos.email().trim())
                .filter(u -> passwordEncoder.matches(datos.password(), u.getPassword()))
                .orElseThrow(() -> new CredencialesInvalidasException("Email o contraseña incorrectos"));

        if (!usuario.isActivo()) {
            throw new CredencialesInvalidasException("La cuenta está desactivada. Comuníquese con la biblioteca");
        }
        if (usuario.getRol() == Rol.LECTOR) {
            sancionService.verificarSinSancion(usuario.getId());
        }

        return new LoginResponseDTO(jwtService.generarToken(usuario), "Bearer", jwtService.getExpiracionMs(),
                usuario.getId(), usuario.getNombreCompleto(), usuario.getEmail(), usuario.getRol().name());
    }
}