// Comprobantes de pago con Yape (panel del personal).
import axiosClient from './axiosClient.js';

/** Los comprobantes que aun no ha revisado nadie. */
export const listarComprobantesPendientes = () => axiosClient.get('/comprobantes/pendientes');

/** Todos los comprobantes (filtro opcional por estado: PENDIENTE, APROBADO, DENEGADO). */
export const listarComprobantes = (estado) => axiosClient.get('/comprobantes', { params: { estado } });

/** Enlace temporal a la captura (caduca en 15 minutos). */
export const urlImagenComprobante = (id) => axiosClient.get(`/comprobantes/${id}/imagen`);

/** Aceptar el comprobante: el lector ya tiene el libro virtual. */
export const aprobarComprobante = (id) => axiosClient.post(`/comprobantes/${id}/aprobar`);

/** Denegar el comprobante: el lector puede subir otra captura. */
export const denegarComprobante = (id, motivo) => axiosClient.post(`/comprobantes/${id}/denegar`, { motivo });
