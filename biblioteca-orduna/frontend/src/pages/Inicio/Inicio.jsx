// Réplica del prototipo Figma "INICIO".
// - Bibliotecario/gerente: KPIs en tiempo real (GET /api/dashboard) + libros destacados.
// - Lector: versión reducida (GET /api/mi/panel): sus préstamos, reservas, deuda y avisos.
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import { getDashboard, getPanel } from '../../api/miCuentaApi.js';
import { useApi, useRefrescoTiempoReal } from '../../hooks.js';
import StatCard from '../../components/common/StatCard.jsx';
import Estado from '../../components/common/Estado.jsx';
import Portada from '../../components/common/Portada.jsx';
import { Cargando, Vacio } from '../../components/common/Cargando.jsx';
import { estadoLibro, estadoPrestamo, estadoReserva } from '../../utils/estados.js';
import { fmtFecha, fmtSoles } from '../../utils/formato.js';
import './Inicio.css';

function Inicio() {
  const { usuario, esLector } = useAuth();
  const navigate = useNavigate();
  const [busqueda, setBusqueda] = useState('');
  const { datos, cargando, recargar } = useApi(() => (esLector ? getPanel() : getDashboard()), [esLector]);
  // El panel son cifras del día: cualquier cambio en la biblioteca puede moverlas.
  useRefrescoTiempoReal(['*'], recargar);

  const primerNombre = usuario?.nombre?.split(' ')[0];
  const buscar = (e) => {
    e.preventDefault();
    navigate(`/libros?q=${encodeURIComponent(busqueda.trim())}`);
  };

  return (
    <div>
      <div className="hero-banner">
        <div className="hero-text">
          <h1>Bienvenido, {primerNombre}</h1>
          <p>
            {esLector
              ? 'Consulta el catálogo, reserva libros y revisa tus préstamos desde aquí.'
              : 'Gestiona y administra todos los procesos de la Biblioteca de manera rápida y sencilla'}
          </p>
          <form className="hero-search" onSubmit={buscar}>
            <input placeholder="Buscar un libro, autor o categoría..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
          </form>
        </div>
      </div>

      {cargando && <Cargando />}

      {!cargando && datos && !esLector && (
        <div className="stats-row">
          <StatCard icono="📚" color="blue" label="Total de libros" value={datos.total_libros} />
          <StatCard icono="⇄" color="amber" label="Préstamos activos" value={datos.prestamos_activos} />
          <StatCard icono="🔖" color="red" label="Reservas en espera" value={datos.reservas_en_espera} />
          <StatCard icono="👥" color="green" label="Usuarios registrados" value={datos.usuarios_registrados} />
        </div>
      )}

      {!cargando && datos && !esLector && (datos.prestamos_vencidos > 0 || datos.multas_pendientes > 0) && (
        <div className="alert-box" style={{ marginBottom: 24 }}>
          <span className="alert-icon">⚠</span>
          <div>
            <b>Atención</b>
            <div className="alert-sub">
              {datos.prestamos_vencidos} préstamo(s) vencido(s) · {datos.multas_pendientes} multa(s) por cobrar
            </div>
          </div>
          <Link to="/devoluciones" className="btn-ghost" style={{ marginLeft: 'auto' }}>Ver devoluciones</Link>
        </div>
      )}

      {!cargando && datos && esLector && <PanelLector datos={datos} />}

      {!cargando && datos && (
        <>
          <div className="block-head">
            <h3>Libros destacados</h3>
            <Link to="/libros">Ver todos</Link>
          </div>
          {datos.libros_destacados?.length ? (
            <div className="destacados-grid">
              {datos.libros_destacados.slice(0, 4).map((libro) => (
                <Link to={`/libros?ver=${libro.id}`} className="libro-card" key={libro.id} style={{ textDecoration: 'none' }}>
                  <Portada libro={libro} alto={120} />
                  <div className="libro-info">
                    <div className="libro-title">{libro.titulo}</div>
                    <div className="libro-author">{libro.autores.map((a) => a.nombre).join(', ')}</div>
                    <Estado valor={libro.estado} mapa={estadoLibro} />
                  </div>
                </Link>
              ))}
            </div>
          ) : (
            <div className="card"><Vacio texto="Aún no hay libros en el catálogo." /></div>
          )}
        </>
      )}
    </div>
  );
}

function PanelLector({ datos }) {
  return (
    <>
      {datos.estado === 'SUSPENDIDO' && (
        <div className="alert-box" style={{ marginBottom: 20 }}>
          <span className="alert-icon">⛔</span>
          <div><b>Cuenta suspendida</b><div className="alert-sub">No puedes pedir préstamos ni reservar mientras dure la sanción.</div></div>
        </div>
      )}
      <div className="stats-row">
        <StatCard icono="⇄" color="blue" label="Mis préstamos activos" value={datos.prestamos_activos.length} />
        <StatCard icono="🔖" color="amber" label="Mis reservas" value={datos.reservas_activas.length} />
        <StatCard icono="💰" color="red" label="Deuda por multas" value={fmtSoles(datos.deuda_multas)} />
        <StatCard icono="🔔" color="green" label="Avisos sin leer" value={datos.notificaciones_no_leidas} />
      </div>

      <div className="dos-columnas" style={{ marginBottom: 26 }}>
        <div className="card table-card">
          <table>
            <thead><tr><th>Libro</th><th>Vence</th><th>Estado</th></tr></thead>
            <tbody>
              {datos.prestamos_activos.length === 0 && <tr><td colSpan={3} className="celda-estado">No tienes préstamos activos.</td></tr>}
              {datos.prestamos_activos.map((p) => (
                <tr key={p.id}>
                  <td>{p.titulo_libro}</td>
                  <td className="mono">{p.fecha_vencimiento ? fmtFecha(p.fecha_vencimiento) : 'Abierto'}</td>
                  <td><Estado valor={p.estado} mapa={estadoPrestamo} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <div className="card" style={{ padding: 18 }}>
          <h3 className="seccion-titulo">Mis reservas</h3>
          {datos.reservas_activas.length === 0 && <p className="hint" style={{ color: 'var(--muted)', fontSize: 13 }}>No tienes reservas activas.</p>}
          {datos.reservas_activas.map((r) => (
            <div key={r.id} className="summary-row">
              <span>{r.titulo_libro}</span>
              {r.estado === 'NOTIFICADA'
                ? <b style={{ color: 'var(--blue-text)' }}>Recoger hasta {fmtFecha(r.fecha_limite_retiro)}</b>
                : <Estado valor={r.estado} mapa={estadoReserva} />}
            </div>
          ))}
        </div>
      </div>
    </>
  );
}

export default Inicio;
