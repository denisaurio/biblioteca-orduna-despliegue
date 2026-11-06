// La caja: el cajón de dinero de la biblioteca (solo el personal).
import axiosClient from './axiosClient.js';

/** La caja abierta (si hay) y el resumen de hoy: total del día, desglose y movimientos. */
export const getCajaActual = () => axiosClient.get('/cajas/actual');

/** Abrir el cajón. datos: { monto_inicial, observaciones } */
export const abrirCaja = (datos) => axiosClient.post('/cajas', datos);

/** Cerrar el cajón con el efectivo contado. datos: { monto_final_declarado, observaciones } */
export const cerrarCaja = (idCaja, datos) => axiosClient.post(`/cajas/${idCaja}/cerrar`, datos);

/** Historial de cajas del rango de fechas (AAAA-MM-DD). */
export const getCajas = (desde, hasta) => axiosClient.get('/cajas', { params: { desde, hasta } });

/** Una caja con todos sus movimientos. */
export const getCaja = (idCaja) => axiosClient.get(`/cajas/${idCaja}`);

/** Qué libros faltan: préstamos sin devolver, reservas y pedidos sin cobrar. */
export const getCajaPendientes = () => axiosClient.get('/cajas/pendientes');
