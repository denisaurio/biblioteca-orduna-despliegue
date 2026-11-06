// Menú lateral igual al prototipo de Figma. Cambia según el rol:
// - Lector: Inicio, Libros, Autores, Mis préstamos, Mis reservas, Avisos.
// - Bibliotecario / Gerente: menú completo + bloque "Administración".
// - Configuración y Auditoría: solo Gerente.
import { NavLink } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import { iniciales, nombreRol } from '../../utils/formato.js';

const iconProps = { viewBox: '0 0 24 24', fill: 'none', stroke: 'currentColor', strokeWidth: 1.7, strokeLinecap: 'round', strokeLinejoin: 'round' };

const I = {
  inicio: <path d="M3 10.5 12 3l9 7.5V21a1 1 0 0 1-1 1h-5v-7H9v7H4a1 1 0 0 1-1-1Z" />,
  libros: <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21.5V5.5Z" />,
  autores: <><circle cx="9" cy="8" r="3.2" /><path d="M2.5 20c0-3.6 2.9-6 6.5-6s6.5 2.4 6.5 6" /></>,
  usuarios: <><circle cx="9" cy="8" r="3" /><path d="M3 20c0-3.3 2.7-5.5 6-5.5s6 2.2 6 5.5" /><circle cx="17" cy="8.5" r="2.3" /><path d="M15.3 14.3c2.6.4 4.4 2.3 4.4 5.2" /></>,
  prestamos: <path d="M7 10h10M7 14h6M4 5h16v14H4Z" />,
  devoluciones: <path d="M4 10h11l-3-3m3 3-3 3M20 14H9l3-3m-3 3 3 3" />,
  reservas: <path d="M12 7v5l3 3M12 21a9 9 0 1 1 0-18 9 9 0 0 1 0 18Z" />,
  ejemplares: <><rect x="4" y="4" width="16" height="16" rx="1.5" /><path d="M8 4v16M4 9h4" /></>,
  pagos: <><rect x="3" y="6" width="18" height="12" rx="1.5" /><path d="M3 10h18" /></>,
  caja: <><rect x="3" y="7" width="18" height="12" rx="1.5" /><path d="M3 11h18M7 15h4" /></>,
  virtual: <><rect x="5" y="2.5" width="14" height="19" rx="2" /><path d="M9 7h6M9 11h6M10.5 18h3" /></>,
  sanciones: <><circle cx="12" cy="12" r="9" /><path d="m8 8 8 8" /></>,
  avisos: <><path d="M6 8a6 6 0 0 1 12 0c0 5 2 6 2 6H4s2-1 2-6Z" /><path d="M10 19a2 2 0 0 0 4 0" /></>,
  bitacora: <><circle cx="12" cy="12" r="9" /><path d="M12 7v5l3 3" /></>,
  reportes: <path d="M4 20V10M10 20V4M16 20v-7M22 20H2" />,
  auditoria: <><path d="M4 5h16v14H4z" /><path d="M8 9h8M8 13h5M8 17h3" /></>,
  config: <><circle cx="12" cy="12" r="3" /><path d="M12 2v3M12 19v3M4.2 4.2l2.1 2.1M17.7 17.7l2.1 2.1M2 12h3M19 12h3M4.2 19.8l2.1-2.1M17.7 6.3l2.1-2.1" /></>,
};

function menuPara(rol) {
  if (rol === 'LECTOR') {
    return {
      general: [
        { to: '/inicio', label: 'Inicio', icon: I.inicio },
        { to: '/libros', label: 'Libros', icon: I.libros },
        { to: '/autores', label: 'Autores', icon: I.autores },
        { to: '/prestamos', label: 'Mis préstamos y multas', icon: I.prestamos },
        { to: '/reservas', label: 'Mis reservas', icon: I.reservas },
        { to: '/mis-libros-virtuales', label: 'Mis libros virtuales', icon: I.virtual },
        { to: '/avisos', label: 'Avisos', icon: I.avisos },
      ],
      admin: [],
    };
  }
  const admin = [
    { to: '/ejemplares', label: 'Ejemplares', icon: I.ejemplares },
    { to: '/libros-virtuales', label: 'Reservas de libro virtual', icon: I.virtual },
    { to: '/pagos-multa', label: 'Pagos de multas', icon: I.pagos },
    { to: '/caja', label: 'Caja', icon: I.caja },
    { to: '/sanciones', label: 'Sanciones y bloqueos', icon: I.sanciones },
    { to: '/avisos', label: 'Avisos', icon: I.avisos },
    { to: '/bitacora', label: 'Bitácora de turnos', icon: I.bitacora },
    { to: '/reportes', label: 'Reportes y cierre', icon: I.reportes },
  ];
  if (rol === 'GERENTE') {
    admin.push({ to: '/auditoria', label: 'Auditoría', icon: I.auditoria });
    admin.push({ to: '/configuracion', label: 'Configuración', icon: I.config });
  }
  return {
    general: [
      { to: '/inicio', label: 'Inicio', icon: I.inicio },
      { to: '/libros', label: 'Libros', icon: I.libros },
      { to: '/autores', label: 'Autores', icon: I.autores },
      { to: '/usuarios', label: 'Usuarios', icon: I.usuarios },
      { to: '/prestamos', label: 'Préstamos', icon: I.prestamos },
      { to: '/devoluciones', label: 'Devoluciones', icon: I.devoluciones },
      { to: '/reservas', label: 'Reservas', icon: I.reservas },
    ],
    admin,
  };
}

function Enlace({ l }) {
  return (
    <NavLink to={l.to} className={({ isActive }) => 'nav-item' + (isActive ? ' active' : '')}>
      <svg {...iconProps}>{l.icon}</svg>
      {l.label}
    </NavLink>
  );
}

function Sidebar() {
  const { usuario } = useAuth();
  const { general, admin } = menuPara(usuario?.rol);

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8"><path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21.5V5.5Z" /></svg>
        <div>
          <div className="brand-line1">Biblioteca</div>
          <div className="brand-line2">Orduña</div>
        </div>
      </div>

      <nav className="nav-scroll">
        {general.map((l) => <Enlace key={l.to} l={l} />)}
        {admin.length > 0 && (
          <>
            <div className="nav-section-label">Administración</div>
            {admin.map((l) => <Enlace key={l.to} l={l} />)}
          </>
        )}
      </nav>

      <NavLink to="/perfil" className="sidebar-foot">
        <div className="avatar-mini">{iniciales(usuario?.nombre)}</div>
        <div className="who">
          <div className="name">{usuario?.nombre}</div>
          <div className="role">{nombreRol[usuario?.rol]}</div>
        </div>
      </NavLink>
    </aside>
  );
}

export default Sidebar;
