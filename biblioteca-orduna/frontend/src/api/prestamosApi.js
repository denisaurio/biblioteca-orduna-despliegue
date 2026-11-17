// RF06 - Tabla prestamos. El bibliotecario sale del JWT.
import axiosClient from './axiosClient.js';

/**
 * datos: { id_libro | id_ejemplar, id_usuario_lector,
 *          tipo_prestamo: 'FECHA_FIJA' (fijo) | 'PERSONALIZADO',
 *          dias_prestamo (obligatorio si es PERSONALIZADO),
 *          tarifa_multa_diaria (opcional: multa personalizada por día; null = la de la configuración),
 *          metodo_pago (cómo paga el precio al prestar; solo cobra el encargado o el gerente) }
 * Si quien presta no es el encargado de la caja, el préstamo queda POR_COBRAR.
 */
export const crearPrestamo = (datos) => axiosClient.post('/prestamos', datos);
/** estado: ACTIVOS | VENCIDOS | DEVUELTOS | POR_COBRAR | TODOS */
export const getPrestamos = (estado = 'ACTIVOS', q = '') => axiosClient.get('/prestamos', { params: { estado, q } });
/** El encargado cobra un préstamo POR_COBRAR: el libro sale recién ahora. */
export const cobrarPrestamo = (id, metodoPago) => axiosClient.post(`/prestamos/${id}/cobrar`, { metodo_pago: metodoPago });
/** El cliente no pagó: se anula y el ejemplar se libera. */
export const anularPrestamo = (id, motivo) => axiosClient.post(`/prestamos/${id}/anular`, { motivo });
