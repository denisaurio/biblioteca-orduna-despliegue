// Mensajes cortos de éxito / error que aparecen abajo a la derecha.
import { createContext, useCallback, useContext, useState } from 'react';

const ToastContext = createContext(null);

export function ToastProvider({ children }) {
  const [toasts, setToasts] = useState([]);

  const mostrar = useCallback((texto, tipo = 'ok') => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, texto, tipo }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), tipo === 'ok' ? 3500 : 7000);
  }, []);

  const api = {
    ok: (texto) => mostrar(texto, 'ok'),
    error: (texto) => mostrar(texto, 'error'),
    /** La operacion salio bien pero algo opcional no (ej. la portada de internet). */
    aviso: (texto) => mostrar(texto, 'aviso'),
  };

  return (
    <ToastContext.Provider value={api}>
      {children}
      <div className="toast-zone">
        {toasts.map((t) => (
          <div key={t.id} className={`toast ${t.tipo}`} role="status">
            {t.tipo === 'ok' ? '✓ ' : '⚠ '}{t.texto}
          </div>
        ))}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  return useContext(ToastContext);
}
