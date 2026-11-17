// CRM del administrador: Ficha 360 del cliente y notas internas (solo gerente).
import axiosClient from './axiosClient.js';

/** Todo de un lector: datos, resumen, préstamos con sus cajas, multas, pagos, sanciones y notas. */
export const getFichaCliente = (idCliente) => axiosClient.get(`/crm/clientes/${idCliente}`);
export const crearNotaCliente = (idCliente, texto) => axiosClient.post(`/crm/clientes/${idCliente}/notas`, { texto });
export const borrarNotaCliente = (idNota) => axiosClient.delete(`/crm/notas/${idNota}`);
