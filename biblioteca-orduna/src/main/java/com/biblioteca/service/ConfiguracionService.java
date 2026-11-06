package com.biblioteca.service;

import com.biblioteca.dto.ConfiguracionDTO;
import com.biblioteca.dto.VigenciaMultaDTO;
import com.biblioteca.entity.Configuracion;
import com.biblioteca.entity.ConfiguracionMulta;
import com.biblioteca.entity.TipoAccionAuditoria;
import com.biblioteca.entity.Usuario;
import com.biblioteca.repository.ConfiguracionMultaRepository;
import com.biblioteca.repository.ConfiguracionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Objects;

/**
 * RF13 - Configuración del sistema (solo el gerente la modifica).
 * - General y avisos: tabla configuracion (una fila).
 * - Multas y reservas: tabla configuracion_multas, versionada por fecha de vigencia.
 */
@Service
public class ConfiguracionService {

    public static final BigDecimal TARIFA_POR_DEFECTO = new BigDecimal("1.00");
    public static final int DIAS_MAX_POR_DEFECTO = 7;
    public static final int DIAS_RETIRO_POR_DEFECTO = 2;

    private final ConfiguracionRepository configuracionRepository;
    private final ConfiguracionMultaRepository multaConfigRepository;
    private final UsuarioService usuarioService;
    private final AuditoriaService auditoriaService;

    public ConfiguracionService(ConfiguracionRepository configuracionRepository,
                                ConfiguracionMultaRepository multaConfigRepository,
                                UsuarioService usuarioService, AuditoriaService auditoriaService) {
        this.configuracionRepository = configuracionRepository;
        this.multaConfigRepository = multaConfigRepository;
        this.usuarioService = usuarioService;
        this.auditoriaService = auditoriaService;
    }

    /** GET /api/configuracion */
    @Transactional
    public ConfiguracionDTO obtenerConfiguracion() {
        return aDTO(obtenerGeneral(), obtenerParametros());
    }

    /**
     * RF13 - PUT /api/configuracion. Solo cambia lo que viene distinto de null.
     * Si cambia la tarifa, los días máximos o el plazo de retiro, se cierra la vigencia
     * actual y se crea una nueva (no retroactivo).
     */
    @Transactional
    public ConfiguracionDTO actualizarConfiguracion(ConfiguracionDTO d, Long idGerente) {
        Usuario gerente = usuarioService.buscarGerente(idGerente);
        LocalDateTime ahora = LocalDateTime.now();

        Configuracion g = obtenerGeneral();
        if (d.nombreBiblioteca() != null) g.setNombreBiblioteca(d.nombreBiblioteca().trim());
        if (d.correoContacto() != null) g.setCorreoContacto(d.correoContacto().trim());
        if (d.telefonoContacto() != null) g.setTelefonoContacto(d.telefonoContacto().trim());
        if (d.horaApertura() != null) g.setHoraApertura(d.horaApertura());
        if (d.horaCierre() != null) g.setHoraCierre(d.horaCierre());
        if (d.avisoVencimiento() != null) g.setAvisoVencimiento(d.avisoVencimiento());
        if (d.avisoReserva() != null) g.setAvisoReserva(d.avisoReserva());
        if (d.avisoMulta() != null) g.setAvisoMulta(d.avisoMulta());
        if (d.avisoNuevoLibro() != null) g.setAvisoNuevoLibro(d.avisoNuevoLibro());
        g.setFechaActualizacion(ahora);
        g = configuracionRepository.save(g);

        ConfiguracionMulta actual = obtenerParametros();
        BigDecimal tarifa = d.tarifaMultaDiaria() != null ? d.tarifaMultaDiaria() : actual.getTarifaMultaDiaria();
        Integer dias = d.diasMax() != null ? d.diasMax() : actual.getDiasMax();
        Integer retiro = d.diasRetiroReserva() != null ? d.diasRetiroReserva() : actual.getDiasRetiroReserva();
        boolean cambio = tarifa.compareTo(actual.getTarifaMultaDiaria()) != 0
                || !Objects.equals(dias, actual.getDiasMax())
                || !Objects.equals(retiro, actual.getDiasRetiroReserva());
        if (cambio) {
            actual.setFechaVigenciaHasta(ahora);
            multaConfigRepository.save(actual);
            ConfiguracionMulta nueva = new ConfiguracionMulta();
            nueva.setTarifaMultaDiaria(tarifa);
            nueva.setDiasMax(dias);
            nueva.setDiasRetiroReserva(retiro);
            nueva.setFechaVigenciaDesde(ahora);
            nueva.setModificadoPor(gerente);
            actual = multaConfigRepository.save(nueva);
        }
        auditoriaService.registrar(idGerente, TipoAccionAuditoria.CAMBIAR_CONFIGURACION, "CONFIGURACION", g.getId(),
                "Cambió la configuración de la biblioteca"
                        + (cambio ? " (tarifa S/ " + tarifa + ", " + dias + " días, retiro en " + retiro + " días)" : ""));
        return aDTO(g, actual);
    }

    /** GET /api/configuracion/historial - versiones de tarifa y días. */
    @Transactional(readOnly = true)
    public List<VigenciaMultaDTO> historial() {
        return multaConfigRepository.findAllByOrderByFechaVigenciaDesdeDesc().stream()
                .map(c -> new VigenciaMultaDTO(c.getId(), c.getTarifaMultaDiaria(), c.getDiasMax(),
                        c.getDiasRetiroReserva(), c.getFechaVigenciaDesde(), c.getFechaVigenciaHasta(),
                        c.getModificadoPor() == null ? null : c.getModificadoPor().getNombreCompleto()))
                .toList();
    }

    /** Parámetros vigentes de multas/reservas. Los usan préstamos, devoluciones y reservas. */
    @Transactional
    public ConfiguracionMulta obtenerParametros() {
        return multaConfigRepository.findFirstByFechaVigenciaHastaIsNullOrderByIdDesc().orElseGet(() -> {
            ConfiguracionMulta c = new ConfiguracionMulta();
            c.setTarifaMultaDiaria(TARIFA_POR_DEFECTO);
            c.setDiasMax(DIAS_MAX_POR_DEFECTO);
            c.setDiasRetiroReserva(DIAS_RETIRO_POR_DEFECTO);
            c.setFechaVigenciaDesde(LocalDateTime.now());
            return multaConfigRepository.save(c);
        });
    }

    /** Datos generales e interruptores de avisos. */
    @Transactional
    public Configuracion obtenerGeneral() {
        return configuracionRepository.findById(Configuracion.ID_UNICO).orElseGet(() -> {
            Configuracion c = new Configuracion();
            c.setNombreBiblioteca("Biblioteca Orduña");
            c.setHoraApertura(LocalTime.of(8, 0));
            c.setHoraCierre(LocalTime.of(20, 0));
            c.setFechaActualizacion(LocalDateTime.now());
            return configuracionRepository.save(c);
        });
    }

    private static ConfiguracionDTO aDTO(Configuracion g, ConfiguracionMulta m) {
        return new ConfiguracionDTO(g.getNombreBiblioteca(), g.getCorreoContacto(), g.getTelefonoContacto(),
                g.getHoraApertura(), g.getHoraCierre(), m.getTarifaMultaDiaria(), m.getDiasMax(),
                m.getDiasRetiroReserva(), g.isAvisoVencimiento(), g.isAvisoReserva(), g.isAvisoMulta(),
                g.isAvisoNuevoLibro());
    }
}
