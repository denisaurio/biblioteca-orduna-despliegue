// Auditoría: quién hizo qué en la biblioteca (solo el gerente).
import axiosClient from './axiosClient.js';

/**
 * params: { desde, hasta, id_usuario, accion, pagina, tamanio }
 * -> { contenido, pagina, tamanio, total_elementos, total_paginas }
 */
export const getAuditoria = (params) => axiosClient.get('/auditoria', { params });
