// RF06 - Tabla prestamos. El bibliotecario sale del JWT.
import axiosClient from './axiosClient.js';

/**
 * datos: { id_libro | id_ejemplar, id_usuario_lector,
 *          tipo_prestamo: 'FECHA_FIJA' (fijo) | 'PERSONALIZADO',
 *          dias_prestamo (obligatorio si es PERSONALIZADO),
 *          tarifa_multa_diaria (opcional: multa personalizada por día; null = la de la configuración) }
 */
export const crearPrestamo = (datos) => axiosClient.post('/prestamos', datos);
/** estado: ACTIVOS | VENCIDOS | DEVUELTOS | TODOS */
export const getPrestamos = (estado = 'ACTIVOS', q = '') => axiosClient.get('/prestamos', { params: { estado, q } });
