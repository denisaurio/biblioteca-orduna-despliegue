package com.biblioteca.tarea;

import com.biblioteca.service.CajaService;
import com.biblioteca.service.CierreDiarioService;
import com.biblioteca.service.NotificacionService;
import com.biblioteca.service.ReservaService;
import com.biblioteca.realtime.RealtimeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Tareas que corren solas, en hora de Lima. */
@Component
public class TareasProgramadas {

    private static final Logger log = LoggerFactory.getLogger(TareasProgramadas.class);

    private final NotificacionService notificacionService;
    private final CierreDiarioService cierreDiarioService;
    private final ReservaService reservaService;
    private final CajaService cajaService;
    private final RealtimeService realtimeService;

    public TareasProgramadas(NotificacionService notificacionService, CierreDiarioService cierreDiarioService,
                             ReservaService reservaService, CajaService cajaService,
                             RealtimeService realtimeService) {
        this.notificacionService = notificacionService;
        this.cierreDiarioService = cierreDiarioService;
        this.reservaService = reservaService;
        this.cajaService = cajaService;
        this.realtimeService = realtimeService;
    }

    /** Horario de caja: cada minuto abre dentro del rango y cierra al llegar la hora de fin. */
    @Scheduled(cron = "0 * * * * *", zone = "America/Lima")
    public void horarioCaja() {
        if (cajaService.abrirAutomaticoPorHorario()) {
            log.info("Caja abierta automáticamente por el horario configurado");
            realtimeService.publicarCambio("cajas", "ABRIR_CAJA_AUTOMATICO");
        }
        if (cajaService.cerrarAutomaticoPorHorario()) {
            log.info("Caja cerrada automáticamente por el horario configurado");
            realtimeService.publicarCambio("cajas", "CERRAR_CAJA_AUTOMATICO");
        }
    }

    /** RF09 - Todos los días a las 00:10: vence reservas no recogidas y pasa el ejemplar al siguiente. */
    @Scheduled(cron = "0 10 0 * * *", zone = "America/Lima")
    public void vencerReservas() {
        int n = reservaService.vencerReservasNoRetiradas();
        if (n > 0) log.info("Reservas vencidas por no recoger: {}", n);
    }

    /** RF11 - Todos los días a las 8:00 a.m.: avisos de vencimiento, préstamos vencidos y multas. */
    @Scheduled(cron = "0 0 8 * * *", zone = "America/Lima")
    public void avisosDiarios() {
        var r = notificacionService.generarAvisosAutomaticos();
        log.info("Avisos generados: {}", r);
    }

    /** RF12 - Todos los días a las 11:55 p.m.: cierre diario si nadie lo hizo manualmente. */
    @Scheduled(cron = "0 55 23 * * *", zone = "America/Lima")
    public void cierreDiario() {
        cierreDiarioService.generarCierreAutomatico()
                .ifPresent(c -> log.info("Cierre diario automático generado: {}", c));
    }
}
