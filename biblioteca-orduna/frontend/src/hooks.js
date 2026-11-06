// Hook para cargar datos de la API con estado de carga y recarga manual.
import { useCallback, useEffect, useState } from 'react';
import { mensajeError } from './api/axiosClient.js';
import { useToast } from './context/ToastContext.jsx';

/**
 * const { datos, cargando, recargar } = useApi(() => getLibros(), [dependencias]);
 * Muestra un toast si la petición falla.
 */
export function useApi(funcion, deps = [], inicial = null) {
  const toast = useToast();
  const [datos, setDatos] = useState(inicial);
  const [cargando, setCargando] = useState(true);

  const recargar = useCallback(async () => {
    setCargando(true);
    try {
      const res = await funcion();
      setDatos(res.status === 204 ? null : res.data);
    } catch (e) {
      toast.error(mensajeError(e));
    } finally {
      setCargando(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, deps);

  useEffect(() => {
    recargar();
  }, [recargar]);

  return { datos, setDatos, cargando, recargar };
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
