// RF10 - Reportes estadísticos exportables (inventario, préstamos, multas) y
// RF12 - Cierre diario (total de préstamos pendientes al final de la jornada).
import { useState } from 'react';
import { getReporte, descargarCsv, generarCierre, getCierres } from '../../api/reportesApi.js';
import { mensajeError } from '../../api/axiosClient.js';
import { useToast } from '../../context/ToastContext.jsx';
import { useApi, useAccion, useRefrescoTiempoReal } from '../../hooks.js';
import StatCard from '../../components/common/StatCard.jsx';
import Estado from '../../components/common/Estado.jsx';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import { Cargando, FilaEstado } from '../../components/common/Cargando.jsx';
import { estadoMulta, estadoPrestamo, nombreMetodo } from '../../utils/estados.js';
import { codigo, fmtFecha, fmtFechaHora, fmtSoles, hoyISO, inicioMesISO } from '../../utils/formato.js';

function Reportes() {
  const [tab, setTab] = useState('inventario');
  const [desde, setDesde] = useState(inicioMesISO());
  const [hasta, setHasta] = useState(hoyISO());
  const [descargando, setDescargando] = useState(false);
  const toast = useToast();

  const descargar = async () => {
    setDescargando(true);
    try {
      await descargarCsv(tab, tab === 'inventario' ? undefined : { desde, hasta });
    } catch (e) {
      toast.error(mensajeError(e));
    } finally {
      setDescargando(false);
    }
  };

  return (
    <div>
      <div className="page-head">
        <div><h1>Reportes y cierre diario</h1><p>Estadísticas exportables (RF10) y cierre de la jornada (RF12).</p></div>
        {tab !== 'cierre' && (
          <button className="btn-primary" onClick={descargar} disabled={descargando}>
            {descargando ? 'Descargando...' : '⬇ Descargar CSV (Excel)'}
          </button>
        )}
      </div>

      <div className="toolbar">
        <div className="tabs">
          {[['inventario', 'Inventario'], ['prestamos', 'Préstamos'], ['multas', 'Multas'], ['cierre', 'Cierre diario']].map(([v, t]) => (
            <button key={v} className={tab === v ? 'active' : ''} onClick={() => setTab(v)}>{t}</button>
          ))}
        </div>
        {tab !== 'inventario' && (
          <div className="filtros-row">
            <div><label>Desde</label><input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} /></div>
            <div><label>Hasta</label><input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} /></div>
          </div>
        )}
      </div>

      {tab === 'inventario' && <Inventario />}
      {tab === 'prestamos' && <ReportePrestamos desde={desde} hasta={hasta} />}
      {tab === 'multas' && <ReporteMultas desde={desde} hasta={hasta} />}
      {tab === 'cierre' && <CierreDiario desde={desde} hasta={hasta} />}
    </div>
  );
}

function Inventario() {
  const { datos: r, cargando, recargar } = useApi(() => getReporte('inventario'), []);
  useRefrescoTiempoReal(['libros', 'ejemplares', 'prestamos', 'reservas'], recargar);
  if (cargando || !r) return <Cargando />;
  return (
    <>
      <div className="stats-row">
        <StatCard icono="📚" color="blue" label="Libros" value={r.total_libros} />
        <StatCard icono="🗂" color="green" label="Ejemplares (disponibles)" value={`${r.total_ejemplares} (${r.disponibles})`} />
        <StatCard icono="⇄" color="amber" label="Prestados / reservados" value={`${r.prestados} / ${r.reservados}`} />
        <StatCard icono="📈" color="red" label="% en uso" value={`${r.porcentaje_en_uso}%`} />
      </div>
      {(r.danados + r.perdidos + r.de_baja) > 0 && (
        <p className="pick-sub" style={{ marginTop: -12, marginBottom: 14 }}>Fuera de circulación: {r.danados} dañados · {r.perdidos} perdidos · {r.de_baja} de baja</p>
      )}
      <div className="card table-card">
        <table>
          <thead><tr><th>Libro</th><th>Autores</th><th>Géneros</th><th>Total</th><th>Disp.</th><th>Prest.</th><th>Reserv.</th><th>Dañ./Perd./Baja</th></tr></thead>
          <tbody>
            {r.detalle.length === 0 && <FilaEstado columnas={8} vacio="No hay libros." />}
            {r.detalle.map((f) => (
              <tr key={f.id_libro}>
                <td>{f.titulo}<div className="pick-sub mono">{f.isbn}</div></td>
                <td>{f.autores}</td>
                <td>{f.generos}</td>
                <td className="mono">{f.total}</td>
                <td className="mono">{f.disponibles}</td>
                <td className="mono">{f.prestados}</td>
                <td className="mono">{f.reservados}</td>
                <td className="mono">{f.danados}/{f.perdidos}/{f.de_baja}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function Ranking({ titulo, filas }) {
  return (
    <div className="card" style={{ padding: 18 }}>
      <h3 className="seccion-titulo">{titulo}</h3>
      {filas.length === 0 ? <p className="pick-sub">Sin datos en el rango.</p> : filas.map((f, i) => (
        <div key={f.nombre} className="summary-row"><span>{i + 1}. {f.nombre}</span><b>{f.cantidad}</b></div>
      ))}
    </div>
  );
}

function ReportePrestamos({ desde, hasta }) {
  const { datos: r, cargando, recargar } = useApi(() => getReporte('prestamos', { desde, hasta }), [desde, hasta]);
  useRefrescoTiempoReal(['prestamos', 'devoluciones'], recargar);
  if (cargando || !r) return <Cargando />;
  return (
    <>
      <div className="stats-row">
        <StatCard icono="⇄" color="blue" label="Préstamos en el rango" value={r.total_prestamos} />
        <StatCard icono="⏳" color="amber" label="Activos / vencidos" value={`${r.activos} / ${r.vencidos_sin_devolver}`} />
        <StatCard icono="✓" color="green" label="Devueltos" value={r.devueltos} />
        <StatCard icono="⚠" color="red" label="% devueltos con retraso" value={`${r.porcentaje_retraso}%`} />
      </div>
      <div className="dos-columnas" style={{ gridTemplateColumns: '1fr 1fr', marginBottom: 20 }}>
        <Ranking titulo="Libros más prestados" filas={r.libros_mas_prestados} />
        <Ranking titulo="Lectores más activos" filas={r.lectores_mas_activos} />
      </div>
      <div className="card table-card">
        <table>
          <thead><tr><th>Préstamo</th><th>Libro</th><th>Lector</th><th>Prestado</th><th>Vence</th><th>Devuelto</th><th>Retraso</th><th>Estado</th></tr></thead>
          <tbody>
            {r.detalle.length === 0 && <FilaEstado columnas={8} vacio="No hay préstamos en el rango." />}
            {r.detalle.map((f) => (
              <tr key={f.id}>
                <td className="mono">{codigo('P', f.id)}</td>
                <td>{f.titulo_libro}<div className="pick-sub mono">{f.codigo_inventario}</div></td>
                <td>{f.lector}<div className="pick-sub">DNI {f.dni}</div></td>
                <td className="mono">{fmtFecha(f.fecha_prestamo)}</td>
                <td className="mono">{f.fecha_vencimiento ? fmtFecha(f.fecha_vencimiento) : 'Abierto'}</td>
                <td className="mono">{f.fecha_devolucion ? fmtFecha(f.fecha_devolucion) : '—'}</td>
                <td className="mono">{f.dias_retraso || '—'}</td>
                <td><Estado valor={f.estado} mapa={estadoPrestamo} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function ReporteMultas({ desde, hasta }) {
  const { datos: r, cargando, recargar } = useApi(() => getReporte('multas', { desde, hasta }), [desde, hasta]);
  useRefrescoTiempoReal(['multas', 'pagos', 'devoluciones'], recargar);
  if (cargando || !r) return <Cargando />;
  return (
    <>
      <div className="stats-row">
        <StatCard icono="🧾" color="blue" label="Multas generadas" value={r.total_multas} />
        <StatCard icono="💰" color="amber" label="Monto total" value={fmtSoles(r.monto_total)} />
        <StatCard icono="✓" color="green" label="Cobrado" value={fmtSoles(r.monto_cobrado)} />
        <StatCard icono="⚠" color="red" label="Por cobrar" value={fmtSoles(r.monto_pendiente)} />
      </div>
      {Object.keys(r.cobrado_por_metodo).length > 0 && (
        <div className="card" style={{ padding: '12px 18px', marginBottom: 16, display: 'flex', gap: 22, flexWrap: 'wrap', fontSize: 13 }}>
          <b>Cobrado por método:</b>
          {Object.entries(r.cobrado_por_metodo).map(([m, v]) => <span key={m}>{nombreMetodo[m]}: <b>{fmtSoles(v)}</b></span>)}
        </div>
      )}
      <div className="card table-card">
        <table>
          <thead><tr><th>Multa</th><th>Lector</th><th>Libro</th><th>Retraso</th><th>Monto</th><th>Pagado</th><th>Último pago</th><th>Estado</th></tr></thead>
          <tbody>
            {r.detalle.length === 0 && <FilaEstado columnas={8} vacio="No hay multas en el rango." />}
            {r.detalle.map((f) => (
              <tr key={f.id}>
                <td className="mono">{codigo('M', f.id)}</td>
                <td>{f.lector}<div className="pick-sub">DNI {f.dni}</div></td>
                <td>{f.titulo_libro}</td>
                <td className="mono">{f.dias_retraso} d</td>
                <td className="mono">{fmtSoles(f.monto)}</td>
                <td className="mono">{fmtSoles(f.monto_pagado)}</td>
                <td className="mono">{f.fecha_ultimo_pago ? fmtFecha(f.fecha_ultimo_pago) : '—'}<div className="pick-sub">{f.metodos_pago}</div></td>
                <td><Estado valor={f.estado} mapa={estadoMulta} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function CierreDiario({ desde, hasta }) {
  const [generar, setGenerar] = useState(false);
  const { datos: cierres, cargando, recargar } = useApi(() => getCierres(desde, hasta), [desde, hasta], []);
  useRefrescoTiempoReal(['cierre-diario', 'prestamos', 'cajas'], recargar);
  const hoy = cierres.find((c) => c.fecha === hoyISO());

  return (
    <>
      <div className="card" style={{ padding: 18, marginBottom: 16, display: 'flex', alignItems: 'center', gap: 16, flexWrap: 'wrap' }}>
        <div style={{ flex: 1 }}>
          <b>Cierre de hoy ({fmtFecha(hoyISO())})</b>
          <div className="pick-sub">
            {hoy ? `Registrado a las ${fmtFechaHora(hoy.fecha_generacion).slice(11)} por ${hoy.generado_por || 'el sistema'}.`
              : 'Aún no se registra. Si nadie lo hace, el sistema lo genera solo a las 11:55 p. m. Requiere que todos los turnos estén cerrados.'}
          </div>
        </div>
        {!hoy && <button className="btn-primary" onClick={() => setGenerar(true)}>Generar cierre de hoy</button>}
      </div>

      <div className="card table-card">
        <table>
          <thead><tr><th>Fecha</th><th>Préstamos del día</th><th>Devoluciones</th><th>Pendientes</th><th>Vencidos</th><th>Reservas en espera</th><th>Multas (monto)</th><th>Recaudado</th><th>Generado por</th></tr></thead>
          <tbody>
            {(cargando || cierres.length === 0) && <FilaEstado columnas={9} cargando={cargando} vacio="No hay cierres en el rango." />}
            {!cargando && cierres.map((c) => (
              <tr key={c.id} title={c.observaciones || ''}>
                <td className="mono">{fmtFecha(c.fecha)}</td>
                <td className="mono">{c.prestamos_del_dia}</td>
                <td className="mono">{c.devoluciones_del_dia}</td>
                <td className="mono"><b>{c.prestamos_pendientes}</b></td>
                <td className="mono">{c.prestamos_vencidos}</td>
                <td className="mono">{c.reservas_en_espera}</td>
                <td className="mono">{c.multas_generadas} ({fmtSoles(c.monto_multas_generadas)})</td>
                <td className="mono">{fmtSoles(c.monto_recaudado)}</td>
                <td>{c.generado_por || 'Automático'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {generar && <GenerarCierre onClose={() => setGenerar(false)} onHecho={() => { setGenerar(false); recargar(); }} />}
    </>
  );
}

function GenerarCierre({ onClose, onHecho }) {
  const [obs, setObs] = useState('');
  const { ejecutar, enviando } = useAccion();
  const enviar = async (e) => {
    e.preventDefault();
    const r = await ejecutar(() => generarCierre(obs), 'Cierre diario registrado');
    if (r) onHecho();
  };
  return (
    <Modal titulo="Generar cierre diario" subtitulo="Se registra el total de préstamos pendientes y el movimiento del día." onClose={onClose}
           pie={<><button className="btn-ghost" onClick={onClose}>Cancelar</button><button className="btn-primary" form="form-cierre" disabled={enviando}>Generar cierre</button></>}>
      <form id="form-cierre" onSubmit={enviar}>
        <Campo label="Observaciones"><textarea value={obs} onChange={(e) => setObs(e.target.value)} maxLength={500} placeholder="Opcional" /></Campo>
      </form>
    </Modal>
  );
}

export default Reportes;
