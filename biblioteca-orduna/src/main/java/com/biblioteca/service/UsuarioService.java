package com.biblioteca.service;

import com.biblioteca.dto.ActualizarUsuarioDTO;
import com.biblioteca.dto.PaginaDTO;
import com.biblioteca.dto.RegistroUsuarioDTO;
import com.biblioteca.dto.UsuarioResponseDTO;
import com.biblioteca.entity.*;
import com.biblioteca.exception.AccesoDenegadoException;
import com.biblioteca.exception.OperacionInvalidaException;
import com.biblioteca.exception.RecursoDuplicadoException;
import com.biblioteca.exception.RecursoNoEncontradoException;
import com.biblioteca.repository.PrestamoRepository;
import com.biblioteca.repository.SancionRepository;
import com.biblioteca.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Set;

/** RF04 - Usuarios. */
@Service
public class UsuarioService {

    private static final Set<Rol> PERSONAL = EnumSet.of(Rol.BIBLIOTECARIO, Rol.GERENTE);

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final SancionRepository sancionRepository;
    private final PrestamoRepository prestamoRepository;
    private final AuditoriaService auditoriaService;

    public UsuarioService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                          SancionRepository sancionRepository, PrestamoRepository prestamoRepository,
                          AuditoriaService auditoriaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.sancionRepository = sancionRepository;
        this.prestamoRepository = prestamoRepository;
        this.auditoriaService = auditoriaService;
    }

    /**
     * RF04 - Registrar lector (auto-registro público o alta desde la pantalla Usuarios).
     * Lanza RecursoDuplicadoException si el DNI o el email ya existen.
     *
     * idActor es quien lo dio de alta: null en el auto-registro público (el lector se
     * registra solo) y el bibliotecario o gerente cuando lo carga desde la pantalla Usuarios.
     */
    @Transactional
    public UsuarioResponseDTO registrarLector(RegistroUsuarioDTO datos, Long idActor) {
        String dni = datos.dni().trim();
        String email = datos.email().trim().toLowerCase();
        if (usuarioRepository.existsByDni(dni)) {
            throw new RecursoDuplicadoException("Ya existe un usuario con el DNI " + dni);
        }
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new RecursoDuplicadoException("Ya existe un usuario con el email " + email);
        }
        Usuario u = new Usuario();
        u.setNombre(datos.nombre().trim());
        u.setApellido(datos.apellido().trim());
        u.setDni(dni);
        u.setEmail(email);
        u.setTelefono(vacioANull(datos.telefono()));
        u.setDireccion(vacioANull(datos.direccion()));
        u.setTipoUsuario(datos.tipoUsuario() != null ? datos.tipoUsuario() : TipoUsuario.ESTUDIANTE);
        u.setPassword(passwordEncoder.encode(datos.password()));
        u.setRol(Rol.LECTOR);
        u.setActivo(true);
        u = usuarioRepository.save(u);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.REGISTRAR_USUARIO, "USUARIO", u.getId(),
                (idActor == null ? "Se registró como lector" : "Registró al lector")
                        + ": " + u.getNombreCompleto() + " (DNI " + u.getDni() + ")");
        return aDTO(u);
    }

    /** Pantalla Usuarios: búsqueda + filtro por rol + paginación. */
    @Transactional(readOnly = true)
    public PaginaDTO<UsuarioResponseDTO> listarUsuarios(String q, Rol rol, Integer pagina, Integer tamanio) {
        String texto = q == null ? "" : q.trim();
        return Paginas.aDTO(usuarioRepository.buscar(texto, rol, Paginas.de(pagina, tamanio)), this::aDTO);
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtenerUsuario(Long idUsuario) {
        return aDTO(buscar(idUsuario));
    }

    /**
     * Editar usuario.
     * - El lector solo puede editar su propio perfil (y no puede cambiar "activo").
     * - El bibliotecario edita lectores y su propio perfil.
     * - El gerente edita a cualquiera.
     */
    @Transactional
    public UsuarioResponseDTO actualizarUsuario(Long idUsuario, ActualizarUsuarioDTO datos, Long idActor) {
        Usuario actor = buscar(idActor);
        Usuario u = buscar(idUsuario);
        boolean esElMismo = actor.getId().equals(u.getId());
        boolean puede = esElMismo
                || actor.getRol() == Rol.GERENTE
                || (actor.getRol() == Rol.BIBLIOTECARIO && u.getRol() == Rol.LECTOR);
        if (!puede) {
            throw new AccesoDenegadoException("No tiene permiso para editar este usuario");
        }
        u.setNombre(datos.nombre().trim());
        u.setApellido(datos.apellido().trim());
        u.setTelefono(vacioANull(datos.telefono()));
        u.setDireccion(vacioANull(datos.direccion()));
        if (datos.tipoUsuario() != null) {
            u.setTipoUsuario(datos.tipoUsuario());
        }
        if (datos.password() != null && !datos.password().isBlank()) {
            u.setPassword(passwordEncoder.encode(datos.password()));
        }
        if (datos.activo() != null && PERSONAL.contains(actor.getRol()) && !esElMismo) {
            u.setActivo(datos.activo());
        }
        u = usuarioRepository.save(u);
        auditoriaService.registrar(idActor, TipoAccionAuditoria.EDITAR_USUARIO, "USUARIO", u.getId(),
                "Editó el usuario " + u.getNombreCompleto() + " (" + u.getDni() + ")");
        return aDTO(u);
    }

    /** ACTIVO / INACTIVO / SUSPENDIDO (calculado, no se guarda). */
    @Transactional(readOnly = true)
    public EstadoUsuario estadoDe(Usuario u) {
        if (!u.isActivo()) return EstadoUsuario.INACTIVO;
        boolean sancionado = sancionRepository
                .existsByUsuarioIdAndFechaFinGreaterThanEqualAndFechaLevantamientoIsNull(u.getId(), LocalDate.now());
        return sancionado ? EstadoUsuario.SUSPENDIDO : EstadoUsuario.ACTIVO;
    }

    // ---------- Ayudantes usados por los otros servicios ----------

    public Usuario buscar(Long idUsuario) {
        return usuarioRepository.findById(idUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el usuario con id " + idUsuario));
    }

    /** Devuelve el usuario solo si es BIBLIOTECARIO o GERENTE. */
    public Usuario buscarPersonal(Long idUsuario) {
        Usuario u = buscar(idUsuario);
        if (!PERSONAL.contains(u.getRol())) {
            throw new OperacionInvalidaException("El usuario " + idUsuario + " no es bibliotecario ni gerente");
        }
        return u;
    }

    public Usuario buscarGerente(Long idUsuario) {
        Usuario u = buscar(idUsuario);
        if (u.getRol() != Rol.GERENTE) {
            throw new OperacionInvalidaException("El usuario " + idUsuario + " no es gerente");
        }
        return u;
    }

    public Usuario buscarLector(Long idUsuario) {
        Usuario u = buscar(idUsuario);
        if (u.getRol() != Rol.LECTOR) {
            throw new OperacionInvalidaException("El usuario " + idUsuario + " no es un lector");
        }
        if (!u.isActivo()) {
            throw new OperacionInvalidaException("La cuenta del lector " + u.getNombreCompleto() + " está desactivada");
        }
        return u;
    }

    public static boolean esPersonal(Usuario u) {
        return PERSONAL.contains(u.getRol());
    }

    public UsuarioResponseDTO aDTO(Usuario u) {
        return new UsuarioResponseDTO(u.getId(), u.getNombre(), u.getApellido(), u.getDni(), u.getEmail(),
                u.getTelefono(), u.getDireccion(), u.getTipoUsuario() == null ? null : u.getTipoUsuario().name(),
                u.getRol().name(), estadoDe(u).name(),
                prestamoRepository.countByLectorIdAndEstado(u.getId(), EstadoPrestamo.ACTIVO),
                u.getFechaRegistro());
    }

    private static String vacioANull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
