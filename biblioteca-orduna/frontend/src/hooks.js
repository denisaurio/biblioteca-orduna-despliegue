// Hooks para cargar datos de la API, ejecutar acciones y refrescar en tiempo real.
import { useCallback, useEffect, useRef, useState } from 'react';
import { mensajeError } from './api/axiosClient.js';
import { useToast } from './context/ToastContext.jsx';
import { recursosAfectados, useRealtime } from './context/RealtimeContext.jsx';

/**
 * const { datos, cargando, recargar } = useApi(() => getLibros(), [dependencias]);
 * Muestra un toast si la petición falla.
 *
 * recargar({ silencioso: true }) vuelve a pedir los datos sin mostrar el cargando:
 * lo usa el refresco automático para no parpadear la pantalla.
 */
export function useApi(funcion, deps = [], inicial = null) {
  const toast = useToast();
  const [datos, setDatos] = useState(inicial);
  const [cargando, setCargando] = useState(true);

  const recargar = useCallback(async (opciones) => {
    const silencioso = opciones?.silencioso === true;
    if (!silencioso) setCargando(true);
    try {
      const res = await funcion();
      setDatos(res.status === 204 ? null : res.data);
    } catch (e) {
      if (!silencioso) toast.error(mensajeError(e));
    } finally {
      if (!silencioso) setCargando(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    recargar();
  }, [recargar]);

  return { datos, setDatos, cargando, recargar };
}

/**
 * Recarga la pantalla cuando otro usuario (o el backend) cambia algo que le afecta.
 *
 *   useRefrescoTiempoReal(['prestamos', 'multas'], recargar)
 *
 * Los eventos llegan por SSE. El recurso del evento se compara con los pedidos y
 * con los recursos que ese cambio arrastra (ver RECURSOS_AFECTADOS). Usa '*' para
 * recargar con cualquier cambio (p. ej. el panel de Inicio).
 */
export function useRefrescoTiempoReal(recursos, recargar, opciones = {}) {
  const { suscribir } = useRealtime();
  const recargarRef = useRef(recargar);
  recargarRef.current = recargar;

  const lista = Array.isArray(recursos) ? recursos : [recursos];
  const clave = lista.filter(Boolean).join('|');
  const habilitado = opciones.habilitado !== false && !!suscribir;

  useEffect(() => {
    if (!habilitado || !clave) return undefined;
    const suscritos = new Set(clave.split('|'));

    return suscribir((evento) => {
      const leToca =
        evento.tipo === 'cambio'
          ? [...suscritos].some((recurso) => recurso === '*' || recursosAfectados(evento.recurso).has(recurso))
          : evento.tipo === 'notificacion'
            ? suscritos.has('*') || suscritos.has('notificaciones')
            : false;

      if (leToca) recargarRef.current?.({ silencioso: true });
    });
  }, [clave, habilitado, suscribir]);
}

/** Ejecuta una acción (POST/PUT/...) con toasts de éxito/error. Devuelve la data o null si falló. */
export function useAccion() {
  const toast = useToast();
  const [enviando, setEnviando] = useState(false);

  const ejecutar = async (funcion, mensajeOk) => {
    setEnviando(true);
    try {
      const res = await funcion();
      if (mensajeOk) toast.ok(mensajeOk);
      // Un 204 sin cuerpo trae data === '' (falso): igual fue un exito.
      return res?.data === '' || res?.data == null ? true : res.data;
    } catch (e) {
      toast.error(mensajeError(e));
      return null;
    } finally {
      setEnviando(false);
    }
  };

  return { ejecutar, enviando };
}

/** Retrasa un valor (para buscar mientras se escribe sin llamar a la API en cada tecla). */
export function useDebounce(valor, ms = 350) {
  const [v, setV] = useState(valor);
  useEffect(() => {
    const t = setTimeout(() => setV(valor), ms);
    return () => clearTimeout(t);
  }, [valor, ms]);
  return v;
}
