// Cliente HTTP central. La URL del backend se cambia solo en el archivo .env (VITE_API_URL).
import axios from 'axios';
import { leerSesion, borrarSesion } from '../utils/sesion.js';

export const API_URL = import.meta.env.VITE_API_URL || 'http://localhost:8080/api';

const axiosClient = axios.create({ baseURL: API_URL });

/** Origen del backend (http://localhost:8080), sin el /api del final. */
const ORIGEN_BACKEND = (() => {
  try {
    return new URL(API_URL, window.location.origin).origin;
  } catch {
    return '';
  }
})();

/**
 * Link de un archivo que puede venir del backend: las portadas que sirve Spring
 * ("/api/archivos/portadas/...") llegan sin host y hay que ponerle el del backend.
 * Las URL completas (R2 publico, Google Books) se devuelven tal cual.
 */
export function urlArchivo(url) {
  if (!url) return '';
  if (/^(https?:|blob:|data:)/i.test(url)) return url;
  return ORIGEN_BACKEND + (url.startsWith('/') ? url : '/' + url);
}

// Adjunta el JWT en cada petición: Authorization: Bearer <token>
axiosClient.interceptors.request.use((config) => {
  const sesion = leerSesion();
  if (sesion?.token) config.headers.Authorization = `Bearer ${sesion.token}`;
  return config;
});

// Si el token venció o es inválido (401), se cierra la sesión y se vuelve al login.
axiosClient.interceptors.response.use(
  (res) => res,
  (error) => {
    const esLogin = error.config?.url?.includes('/auth/login');
    if (error.response?.status === 401 && !esLogin) {
      borrarSesion();
      if (!window.location.pathname.startsWith('/login')) {
        window.location.href = '/login?expirada=1';
      }
    }
    return Promise.reject(error);
  }
);

/**
 * Convierte cualquier error de axios en un texto para mostrar al usuario.
 * El backend responde: { mensaje, errores: { campo: "detalle" } }
 */
export function mensajeError(error) {
  if (!error.response) {
    return 'No se pudo conectar con el servidor. Verifique que el backend esté encendido en ' + API_URL;
  }
  const data = error.response.data || {};
  if (data.errores && Object.keys(data.errores).length) {
    return Object.entries(data.errores).map(([campo, msg]) => `${campo}: ${msg}`).join(' · ');
  }
  return data.mensaje || `Error ${error.response.status}`;
}

export default axiosClient;
