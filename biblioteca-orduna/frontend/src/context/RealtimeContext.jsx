// Canal SSE (Server-Sent Events) del backend: mantenimiento en vivo de avisos y pantallas.
//
// Abre UNA conexión por pestaña en GET /api/eventos. El JWT viaja en ?token= porque
// EventSource no puede mandar la cabecera Authorization.
//
// El backend manda dos tipos de evento:
//   cambio ....... cualquier POST/PUT/PATCH/DELETE terminó bien; lleva el recurso.
//   notificacion . aviso nuevo o cambio de leídos; llega solo a su destinatario.
//
// Las pantallas se suscriben con useRefrescoTiempoReal (ver hooks.js).
import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { API_URL } from '../api/axiosClient.js';
import { useAuth } from './AuthContext.jsx';

const RealtimeContext = createContext(null);

/**
 * Qué otras pantallas quedan desactualizadas cuando cambia un recurso.
 * Si se devuelve un ejemplar, por ejemplo, también cambian préstamos, multas,
 * reservas y la caja: todas esas pantallas deben recargarse.
 */
const RECURSOS_AFECTADOS = {
  prestamos: ['reservas', 'libros', 'ejemplares', 'multas', 'cajas', 'usuarios'],
  devoluciones: ['prestamos', 'multas', 'reservas', 'libros', 'ejemplares', 'cajas', 'sanciones'],
  reservas: ['libros', 'ejemplares', 'prestamos'],
  multas: ['prestamos', 'cajas', 'usuarios'],
  'accesos-virtuales': ['comprobantes', 'cajas', 'libros', 'usuarios'],
  comprobantes: ['accesos-virtuales', 'cajas'],
  cajas: ['accesos-virtuales', 'multas', 'prestamos', 'comprobantes'],
  libros: ['ejemplares', 'reservas', 'autores'],
  ejemplares: ['libros', 'prestamos', 'reservas'],
  autores: ['libros'],
  usuarios: ['prestamos', 'reservas', 'multas', 'sanciones'],
  sanciones: ['usuarios', 'prestamos', 'reservas'],
  turnos: [],
  configuracion: ['notificaciones'],
  'cierre-diario': ['cajas'],
};

export function RealtimeProvider({ children }) {
  const { token } = useAuth();
  const [estado, setEstado] = useState('desconectado'); // conectando | conectado | reconectando | desconectado
  const [ultimoCambio, setUltimoCambio] = useState(null);
  const oyentes = useRef(new Set());

  useEffect(() => {
    if (!token) {
      setEstado('desconectado');
      return undefined;
    }

    const base = API_URL.replace(/\/$/, '');
    const url = `${base}/eventos?token=${encodeURIComponent(token)}`;
    let fuente = null;
    let cerrado = false;

    const abrir = () => {
      if (cerrado) return;
      setEstado((e) => (e === 'conectado' ? e : 'conectando'));
      fuente = new EventSource(url);

      fuente.onopen = () => setEstado('conectado');

      fuente.onmessage = (mensaje) => {
        let evento;
        try {
          evento = JSON.parse(mensaje.data);
        } catch {
          return; // latido u otro dato que no es JSON
        }
        if (evento.tipo === 'cambio') {
          setUltimoCambio(evento);
        }
        if (evento.tipo === 'notificacion') {
          // La campanita del Topbar ya escucha este evento del navegador.
          window.dispatchEvent(new CustomEvent('avisos-actualizados', { detail: evento }));
        }
        oyentes.current.forEach((fn) => {
          try {
            fn(evento);
          } catch {
            // Un oyente roto no debe tumbar la conexión ni a los demás.
          }
        });
      };

      fuente.onerror = () => {
        // EventSource reintenta solo (con espera creciente). Si el token venció,
        // el backend responde 401 y aquí solo queda mostrarlo como "reconectando".
        if (!cerrado) setEstado(fuente.readyState === EventSource.CLOSED ? 'desconectado' : 'reconectando');
      };
    };

    abrir();

    return () => {
      cerrado = true;
      fuente?.close();
      setEstado('desconectado');
    };
  }, [token]);

  /** Registra un oyente de eventos SSE. Devuelve la función para desuscribirse. */
  const suscribir = useCallback((fn) => {
    oyentes.current.add(fn);
    return () => oyentes.current.delete(fn);
  }, []);

  const valor = useMemo(() => ({ estado, ultimoCambio, suscribir }), [estado, ultimoCambio, suscribir]);

  return <RealtimeContext.Provider value={valor}>{children}</RealtimeContext.Provider>;
}

export function useRealtime() {
  return useContext(RealtimeContext);
}

/** Recursos que hay que recargar cuando cambia `recurso` (incluye el propio). */
export function recursosAfectados(recurso) {
  return new Set([recurso, ...(RECURSOS_AFECTADOS[recurso] || [])]);
}
