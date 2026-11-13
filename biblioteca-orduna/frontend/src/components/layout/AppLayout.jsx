// Shell compartido por todas las páginas autenticadas: Sidebar + Topbar + contenido.
//
// Modo invitado (solo visual, sin cuenta ni token): el invitado puede recorrer
// la pantalla de Inicio, pero cualquier clic, envío de formulario o Enter muestra
// el aviso "Un bibliotecario te debe crear tu cuenta". No se bloquea la acción
// de "Salir" (lleva al login).
import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Sidebar from './Sidebar.jsx';
import Topbar from './Topbar.jsx';
import { useAuth } from '../../context/AuthContext.jsx';
import './Invitado.css';

function AppLayout({ children }) {
  const { esInvitado, logout } = useAuth();
  const navigate = useNavigate();
  const [aviso, setAviso] = useState(false);

  useEffect(() => {
    if (!esInvitado) {
      setAviso(false);
      return undefined;
    }

    // Se escucha en fase de captura: así se cancela ANTES de que reaccionen los
    // enlaces y botones de la pantalla (React escucha más abajo en el árbol).
    const permitido = (target) =>
      target instanceof Element && target.closest('[data-invitado-permitido]');

    const bloquear = (e) => {
      if (permitido(e.target)) return;
      e.preventDefault();
      e.stopPropagation();
      setAviso(true);
    };

    const bloquearEnter = (e) => {
      if (e.key !== 'Enter') return;
      if (permitido(e.target)) return;
      if (!(e.target instanceof Element)) return;
      if (!/^(INPUT|SELECT|TEXTAREA|BUTTON|A)$/.test(e.target.tagName)) return;
      e.preventDefault();
      e.stopPropagation();
      setAviso(true);
    };

    const cerrarConEscape = (e) => {
      if (e.key === 'Escape') setAviso(false);
    };

    document.addEventListener('click', bloquear, true);
    document.addEventListener('submit', bloquear, true);
    document.addEventListener('keydown', bloquearEnter, true);
    document.addEventListener('keydown', cerrarConEscape);
    return () => {
      document.removeEventListener('click', bloquear, true);
      document.removeEventListener('submit', bloquear, true);
      document.removeEventListener('keydown', bloquearEnter, true);
      document.removeEventListener('keydown', cerrarConEscape);
    };
  }, [esInvitado]);

  const irAlLogin = () => {
    setAviso(false);
    logout();
    navigate('/login');
  };

  return (
    <div className="app-shell">
      <Sidebar />
      <div className="app-main">
        <Topbar />
        {esInvitado && (
          <div className="invitado-banner" role="note">
            <span className="invitado-banner-icono" aria-hidden="true">👋</span>
            Estás explorando como <b>invitado</b>. Puedes ver la pantalla, pero para usar la
            biblioteca un bibliotecario debe crear tu cuenta.
          </div>
        )}
        <div className="app-content">{children}</div>
      </div>

      {aviso && (
        <div
          className="invitado-overlay"
          data-invitado-permitido="true"
          onClick={() => setAviso(false)}
        >
          <div
            className="invitado-modal"
            role="dialog"
            aria-modal="true"
            aria-labelledby="invitado-titulo"
            onClick={(e) => e.stopPropagation()}
          >
            <div className="invitado-modal-icono" aria-hidden="true">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round">
                <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21.5V5.5Z" />
                <path d="M9 8h7M9 11.5h5" />
              </svg>
            </div>
            <h3 id="invitado-titulo">Un bibliotecario te debe crear tu cuenta</h3>
            <p>
              Estás en modo invitado. Para pedir libros, reservar o ver tu información
              necesitas una cuenta de lector.
            </p>
            <p className="invitado-modal-pista">
              Acércate al mostrador de la biblioteca con tu DNI y pide que te registren.
            </p>
            <div className="invitado-modal-botones">
              <button type="button" className="btn-secundario" onClick={() => setAviso(false)} autoFocus>
                Seguir mirando
              </button>
              <button type="button" className="btn-primario" onClick={irAlLogin}>
                Iniciar sesión
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}

export default AppLayout;
