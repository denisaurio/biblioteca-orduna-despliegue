// Libros virtuales: pedidos del lector, pagos y panel del mostrador.
import axiosClient from './axiosClient.js';

// ---------- Lector ----------
/** datos: { id_libro, tipo: 'ALQUILER'|'COMPRA', dias_alquiler, metodo_pago } */
export const pedirLibroVirtual = (datos) => axiosClient.post('/accesos-virtuales', datos);
export const misLibrosVirtuales = () => axiosClient.get('/accesos-virtuales/mis-libros');
/** Enlace firmado (15 min). descargar=true: el navegador guarda el PDF (solo compras). */
export const urlPdf = (id, descargar = false) =>
  axiosClient.get(`/accesos-virtuales/${id}/pdf`, { params: { descargar } });
export const cancelarPedido = (id) => axiosClient.post(`/accesos-virtuales/${id}/cancelar`);

/**
 * Sube la captura del pago con Yape. Va como multipart: la imagen en el campo
 * "comprobante" y el codigo de operacion (opcional) en "referencia_yape".
 */
export const subirComprobante = (id, formData) =>
  axiosClient.post(`/accesos-virtuales/${id}/comprobante`, formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });

/** Las capturas que subio el lector en este pedido. */
export const misComprobantes = (id) => axiosClient.get(`/accesos-virtuales/${id}/comprobantes`);

// ---------- Mostrador (bibliotecario o gerente) ----------
export const listarPedidos = (estado) => axiosClient.get('/accesos-virtuales/pedidos', { params: { estado } });
export const buscarPedidoPorCodigo = (codigo) =>
  axiosClient.get(`/accesos-virtuales/codigo/${encodeURIComponent(codigo)}`);
export const cambiarPedido = (id, datos) => axiosClient.post(`/accesos-virtuales/${id}/cambiar`, datos);
export const cobrarPedido = (id) => axiosClient.post(`/accesos-virtuales/${id}/cobrar`);
export const accesosDeLibro = (idLibro) => axiosClient.get(`/accesos-virtuales/libro/${idLibro}`);
