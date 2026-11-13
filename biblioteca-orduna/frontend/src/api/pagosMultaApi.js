// RF15 - Tablas multas y pagos_multa.
import axiosClient from './axiosClient.js';

/** estado: PENDIENTES (incluye parciales) | PAGADAS | TODAS */
export const getMultas = (estado = 'PENDIENTES') => axiosClient.get('/multas', { params: { estado } });
/** datos: { monto_pagado, metodo_pago } */
export const pagarMulta = (idMulta, datos) => axiosClient.post(`/multas/${idMulta}/pagar`, datos);
export const getPagos = (desde, hasta) => axiosClient.get('/multas/pagos', { params: { desde, hasta } });

/** Devoluciones que todavía no tienen multa (selector de "+ Registrar multa"). */
export const getDevolucionesSinMulta = () => axiosClient.get('/multas/sin-generar');
/** datos: { id_devolucion, monto, concepto } */
export const crearMulta = (datos) => axiosClient.post('/multas', datos);
