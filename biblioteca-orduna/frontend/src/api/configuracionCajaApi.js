// Horario de la caja: el gerente lo configura y el personal lo consulta.
import axiosClient from './axiosClient.js';

/** Todos los horarios (solo gerente). */
export const getHorariosCaja = () => axiosClient.get('/configuracion-caja');

/** Bibliotecarios activos que pueden ser encargados (solo gerente). */
export const getEncargadosCaja = () => axiosClient.get('/configuracion-caja/encargados');

/** Mis horarios y el de hoy (bibliotecario o gerente). */
export const getMiHorarioCaja = () => axiosClient.get('/configuracion-caja/mi-horario');

/** Reemplaza todos los horarios. Cada fila: { dia_semana, hora_inicio, hora_fin, id_encargado }. */
export const guardarHorariosCaja = (horarios) => axiosClient.put('/configuracion-caja', { horarios });
