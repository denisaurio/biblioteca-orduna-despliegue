// RF07, RF08 - Tabla devoluciones (la multa se calcula en el backend).
import axiosClient from './axiosClient.js';

/**
 * datos: { id_prestamo, observaciones, estado_ejemplar: 'DISPONIBLE' | 'DANADO' | 'PERDIDO',
 *          metodo_pago, cargo_danado }
 * cargo_danado: monto extra de la multa cuando el libro vuelve dañado o perdido (0 = no cobrar).
 */
export const registrarDevolucion = (datos) => axiosClient.post('/devoluciones', datos);
