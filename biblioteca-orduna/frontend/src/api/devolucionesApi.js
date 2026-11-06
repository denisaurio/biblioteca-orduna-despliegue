// RF07, RF08 - Tabla devoluciones (la multa se calcula en el backend).
import axiosClient from './axiosClient.js';

/** datos: { id_prestamo, observaciones, estado_ejemplar: 'DISPONIBLE' | 'DANADO' | 'PERDIDO', metodo_pago } */
export const registrarDevolucion = (datos) => axiosClient.post('/devoluciones', datos);
