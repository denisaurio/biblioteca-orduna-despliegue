// Diagnostico del almacenamiento de archivos (Cloudflare R2).
import axiosClient from './axiosClient.js';

/** GET /api/almacenamiento/estado -> credenciales, buckets y URL publica. */
export const getEstadoAlmacenamiento = () => axiosClient.get('/almacenamiento/estado');

/** POST /api/almacenamiento/probar-escritura -> sube un archivo vacio de prueba. */
export const probarEscrituraAlmacenamiento = () => axiosClient.post('/almacenamiento/probar-escritura');

/** POST /api/almacenamiento/preparar -> crea en R2 los buckets que falten. */
export const prepararAlmacenamiento = () => axiosClient.post('/almacenamiento/preparar');
