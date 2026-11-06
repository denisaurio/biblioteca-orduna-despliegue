// RF09 - Tabla reservas (cola FIFO).
import axiosClient from './axiosClient.js';

export const crearReserva = (idLibro) => axiosClient.post('/reservas', { id_libro: idLibro });
/** Personal: anota a un lector en la cola desde el mostrador. */
export const crearReservaMostrador = (idLibro, idUsuarioLector) =>
  axiosClient.post('/reservas/mostrador', { id_libro: idLibro, id_usuario_lector: idUsuarioLector });
/** estado: PENDIENTE | NOTIFICADA | ATENDIDA | CANCELADA | VENCIDA | (vacío = todas) */
export const getReservas = (estado) => axiosClient.get('/reservas', { params: { estado: estado || undefined } });
export const getColaLibro = (idLibro) => axiosClient.get(`/reservas/cola/${idLibro}`);
export const cancelarReserva = (id) => axiosClient.patch(`/reservas/${id}/cancelar`);
