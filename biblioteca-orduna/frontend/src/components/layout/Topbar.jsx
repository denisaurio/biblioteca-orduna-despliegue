// Barra superior: buscador global (va al catálogo), campanita con avisos sin leer y menú del usuario.
import { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import { useRealtime } from '../../context/RealtimeContext.jsx';
import { contarNoLeidas } from '../../api/notificacionesApi.js';
import { iniciales, nombreRol } from '../../utils/formato.js';
import SearchInput from '../common/SearchInput.jsx';

const ESTADO_TIEMPO_REAL = {
  conectado: 'Actualizaciones en tiempo real activas',
  conectando: 'Conectando actualizaciones en tiempo real...',
  reconectando: 'Reconectando actualizaciones en tiempo real...',
  desconectado: 'Sin actualizaciones en tiempo real',
};

function Topbar() {
  const { usuario, logout } = useAuth();
  const { estado } = useRealtime();
  const navigate = useNavigate();
  const [busqueda, setBusqueda] = useState('');
  const [noLeidas, setNoLeidas] = useState(0);
  const [menu, setMenu] = useState(false);
  const menuRef = useRef(null);

  // Contador de avisos sin leer: al entrar y cada 60 segundos.
  useEffect(() => {
    const cargar = () => contarNoLeidas().then(({ data }) => setNoLeidas(data.total)).catch(() => {});
    cargar();
    const t = setInterval(cargar, 60000);
    window.addEventListener('avisos-actualizados', cargar);
    return () => { clearInterval(t); window.removeEventListener('avisos-actualizados', cargar); };
  }, []);

  useEffect(() => {
    const cerrar = (e) => menuRef.current && !menuRef.current.contains(e.target) && setMenu(false);
    document.addEventListener('mousedown', cerrar);
    return () => document.removeEventListener('mousedown', cerrar);
  }, []);

  const buscar = (texto) => navigate(`/libros?q=${encodeURIComponent(texto.trim())}`);

  const salir = () => {
    logout();
    navigate('/login');
  };

  return (
    <div className="topbar">
      <SearchInput
        placeholder="Buscar libros, autores o géneros..."
        value={busqueda}
        onChange={(e) => setBusqueda(e.target.value)}
        onEnter={buscar}
      />
      <div className="topbar-right">
        <span
          className={`realtime-dot ${estado}`}
          title={ESTADO_TIEMPO_REAL[estado] || ESTADO_TIEMPO_REAL.desconectado}
          aria-label={ESTADO_TIEMPO_REAL[estado] || ESTADO_TIEMPO_REAL.desconectado}
        />
        <button className="icon-btn campana" title="Avisos" onClick={() => navigate('/avisos')}>
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.7"><path d="M6 8a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6Z" /><path d="M10 19a2 2 0 0 0 4 0" /></svg>
          {noLeidas > 0 && <span className="campana-num">{noLeidas > 99 ? '99+' : noLeidas}</span>}
        </button>
        <div className="topbar-user" ref={menuRef} onClick={() => setMenu(!menu)}>
          <div className="avatar">{iniciales(usuario?.nombre)}</div>
          <div className="who">
            <div className="name">{usuario?.nombre}</div>
            <div className="role">{nombreRol[usuario?.rol]}</div>
          </div>
          <span className="caret">▾</span>
          {menu && (
            <div className="user-menu card">
              <button onClick={() => navigate('/perfil')}>Mi perfil</button>
              <button onClick={salir}>Cerrar sesión</button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

export default Topbar;
