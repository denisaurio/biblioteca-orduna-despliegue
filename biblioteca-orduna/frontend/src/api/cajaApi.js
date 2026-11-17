// La caja: el cajón de dinero de la biblioteca (solo el personal).
import axiosClient from './axiosClient.js';

/**
 * La caja abierta (si hay) y el resumen de hoy: total del día, desglose, movimientos,
 * puedo_cobrar (soy el encargado o el gerente) y por_cobrar (préstamos que esperan su cobro).
 */
export const getCajaActual = () => axiosClient.get('/cajas/actual');

/** Abrir el cajón (solo gerente). datos: { monto_inicial, id_encargado, observaciones } */
export const abrirCaja = (datos) => axiosClient.post('/cajas', datos);

/**
 * Cerrar el cajón (solo gerente). datos: { monto_final_declarado, observaciones }.
 * Sin monto_final_declarado se usa el que contó el encargado (arqueo).
 */
export const cerrarCaja = (idCaja, datos) => axiosClient.post(`/cajas/${idCaja}/cerrar`, datos);

/** Cambiar el encargado sin cerrar la caja (solo gerente). */
export const cambiarEncargadoCaja = (idCaja, idEncargado) =>
  axiosClient.put(`/cajas/${idCaja}/encargado`, { id_encargado: idEncargado });

/** El encargado declara el efectivo contado. datos: { monto_contado, observaciones } */
export const arquearCaja = (idCaja, datos) => axiosClient.post(`/cajas/${idCaja}/arqueo`, datos);

/** Historial de cajas del rango de fechas (AAAA-MM-DD). */
export const getCajas = (desde, hasta) => axiosClient.get('/cajas', { params: { desde, hasta } });

/** Una caja con sus movimientos y los préstamos que empezaron y terminaron en ella. */
export const getCaja = (idCaja) => axiosClient.get(`/cajas/${idCaja}`);

/** Qué libros faltan: préstamos sin devolver, reservas y pedidos sin cobrar. */
export const getCajaPendientes = () => axiosClient.get('/cajas/pendientes');
