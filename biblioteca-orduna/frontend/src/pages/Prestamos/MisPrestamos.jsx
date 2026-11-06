// Interfaz "Mis préstamos y multas" del lector: GET /api/mi/prestamos y /api/mi/multas.
import { getMisPrestamos, getMisMultas } from '../../api/miCuentaApi.js';
import { useApi, useRefrescoTiempoReal } from '../../hooks.js';
import Estado from '../../components/common/Estado.jsx';
import StatCard from '../../components/common/StatCard.jsx';
import { FilaEstado } from '../../components/common/Cargando.jsx';
import { estadoMulta, estadoPrestamo } from '../../utils/estados.js';
import { fmtFecha, fmtSoles } from '../../utils/formato.js';

function MisPrestamos() {
  const { datos: prestamos, cargando, recargar: recargarPrestamos } = useApi(() => getMisPrestamos(), [], []);
  const { datos: multas, cargando: cargandoM, recargar: recargarMultas } = useApi(() => getMisMultas(), [], []);
  useRefrescoTiempoReal(['prestamos', 'multas'], () => {
    recargarPrestamos({ silencioso: true });
    recargarMultas({ silencioso: true });
  });

  const activos = prestamos.filter((p) => p.estado !== 'DEVUELTO');
  const deuda = multas.reduce((s, m) => s + Number(m.saldo), 0);

  return (
    <div>
      <div className="page-head">
        <div><h1>Mis préstamos y multas</h1><p>Tus préstamos activos, historial y multas acumuladas.</p></div>
      </div>

      <div className="stats-row">
        <StatCard icono="⇄" color="blue" label="Préstamos activos" value={activos.length} />
        <StatCard icono="⚠" color="red" label="Con retraso" value={activos.filter((p) => p.estado === 'VENCIDO').length} />
        <StatCard icono="📚" color="green" label="Total de préstamos" value={prestamos.length} />
        <StatCard icono="💰" color="amber" label="Deuda por multas" value={fmtSoles(deuda)} />
      </div>

      {deuda > 0 && (
        <div className="alert-box" style={{ marginBottom: 18 }}>
          <span className="alert-icon">💰</span>
          <div><b>Tienes multas pendientes por {fmtSoles(deuda)}</b>
            <div className="alert-sub">No podrás pedir nuevos préstamos hasta pagarlas en el mostrador.</div></div>
        </div>
      )}

      <h3 className="seccion-titulo">Préstamos</h3>
      <div className="card table-card" style={{ marginBottom: 24 }}>
        <table>
          <thead><tr><th>Libro</th><th>Ejemplar</th><th>Prestado</th><th>Devolver hasta</th><th>Retraso</th><th>Estado</th></tr></thead>
          <tbody>
            {(cargando || prestamos.length === 0) && <FilaEstado columnas={6} cargando={cargando} vacio="Aún no tienes préstamos." />}
            {!cargando && prestamos.map((p) => (
              <tr key={p.id}>
                <td>{p.titulo_libro}</td>
                <td className="mono">{p.codigo_inventario}</td>
                <td className="mono">{fmtFecha(p.fecha_prestamo)}</td>
                <td className="mono">{p.fecha_vencimiento ? fmtFecha(p.fecha_vencimiento) : 'Abierto'}</td>
                <td className="mono">{p.dias_retraso > 0 ? `${p.dias_retraso} día(s)` : '—'}</td>
                <td><Estado valor={p.estado} mapa={estadoPrestamo} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h3 className="seccion-titulo">Multas</h3>
      <div className="card table-card">
        <table>
          <thead><tr><th>Libro</th><th>Fecha</th><th>Días de retraso</th><th>Monto</th><th>Pagado</th><th>Saldo</th><th>Estado</th></tr></thead>
          <tbody>
            {(cargandoM || multas.length === 0) && <FilaEstado columnas={7} cargando={cargandoM} vacio="No tienes multas. ¡Bien hecho!" />}
            {!cargandoM && multas.map((m) => (
              <tr key={m.id}>
                <td>{m.titulo_libro}</td>
                <td className="mono">{fmtFecha(m.fecha_generacion)}</td>
                <td className="mono">{m.dias_retraso}</td>
                <td className="mono">{fmtSoles(m.monto)}</td>
                <td className="mono">{fmtSoles(m.monto_pagado)}</td>
                <td className="mono">{fmtSoles(m.saldo)}</td>
                <td><Estado valor={m.estado} mapa={estadoMulta} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default MisPrestamos;
