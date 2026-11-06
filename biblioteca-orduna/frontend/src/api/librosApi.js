// RF01, RF02 - Tabla libros + libros_autores + libros_generos.
import axiosClient from './axiosClient.js';

export const getLibros = () => axiosClient.get('/libros');
export const buscarLibros = (q) => axiosClient.get('/libros/buscar', { params: { q } });
export const getLibroPorId = (id) => axiosClient.get(`/libros/${id}`);
export const getDestacados = () => axiosClient.get('/libros/destacados');
/** datos: { titulo, isbn, editorial, anio_publicacion, num_paginas, ids_autores: [], ids_generos: [] } */
export const crearLibro = (datos) => axiosClient.post('/libros', datos);
export const actualizarLibro = (id, datos) => axiosClient.put(`/libros/${id}`, datos);

export const getGeneros = () => axiosClient.get('/generos');
export const crearGenero = (nombre) => axiosClient.post('/generos', { nombre });

// ---------- Libros virtuales, portadas y PDF ----------

/**
 * Escanea un ISBN y devuelve los datos que rellenan el formulario solos
 * (titulo, autores, editorial, anio, paginas y la portada encontrada).
 */
export const buscarPorIsbn = (isbn) =>
  axiosClient.get(`/libros/isbn/${encodeURIComponent(isbn)}`);

/**
 * Registra el libro con sus ejemplares, la portada y el PDF en un solo envio.
 * datos: RegistroLibroCompletoDTO | portada: File | pdf: File
 */
export const crearLibroCompleto = (datos, portada, pdf) => {
  const form = new FormData();
  form.append('libro', new Blob([JSON.stringify(datos)], { type: 'application/json' }));
  if (portada) form.append('portada', portada);
  if (pdf) form.append('pdf', pdf);
  return axiosClient.post('/libros/completo', form);
};

/**
 * Edita el libro y, si vienen, la portada y el PDF nuevos.
 * Los ejemplares se editan en el modulo Ejemplares, no aqui.
 */
export const actualizarLibroCompleto = (id, datos, portada, pdf) => {
  const form = new FormData();
  form.append('libro', new Blob([JSON.stringify(datos)], { type: 'application/json' }));
  if (portada) form.append('portada', portada);
  if (pdf) form.append('pdf', pdf);
  return axiosClient.put(`/libros/${id}/completo`, form);
};

/** Sube (o reemplaza) la portada de un libro ya registrado. */
export const subirPortada = (id, archivo) => {
  const form = new FormData();
  form.append('portada', archivo);
  return axiosClient.post(`/libros/${id}/portada`, form);
};

/** Sube (o reemplaza) el PDF de un libro virtual. */
export const subirPdf = (id, archivo) => {
  const form = new FormData();
  form.append('pdf', archivo);
  return axiosClient.post(`/libros/${id}/pdf`, form);
};
