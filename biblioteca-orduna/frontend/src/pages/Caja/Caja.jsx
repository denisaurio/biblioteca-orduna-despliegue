// La caja: el cajón de dinero de la biblioteca (solo bibliotecario y gerente).
//
// Es UNA SOLA caja para toda la biblioteca: se abre con el dinero que ya hay en el cajón
// y se cierra con el efectivo contado (el sistema avisa si sobra o falta). El dinero solo
// entra por los movimientos, asi que el total del dia es la suma de los movimientos.
//
// La caja NO es el turno (la bitacora del personal) ni el cierre diario: son tres cosas
// distintas que conviven.
import { useState } from 'react';
import {
  abrirCaja,
  cerrarCaja,
  getCaja,
  getCajaActual,
  getCajaPendientes,
  getCajas,
} from '../../api/cajaApi.js';
import { useApi, useAccion } from '../../hooks.js';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import Estado from '../../components/common/Estado.jsx';
import StatCard from '../../components/common/StatCard.jsx';
import { Cargando, FilaEstado } from '../../components/common/Cargando.jsx';
import {
  estadoCaja,
  estadoComprobante,
  metodoPagoVirtual,
  nombreMetodo,
  tipoAccesoVirtual,
  tipoMovimientoCaja,
} from '../../utils/estados.js';
import { fmtFecha, fmtFechaHora, fmtSoles, hoyISO, inicioMesISO } from '../../utils/formato.js';
import './Caja.css';

function Caja() {
  const [pestana, setPestana] = useState('HOY');
  const { datos: resumen, cargando, recargar } = useApi(() => getCajaActual(), [], null);

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Caja</h1>
          <p>El cajon de la biblioteca: cuanto entro, por que metodo y que libros faltan.</p>
        </div>
      </div>

      <div className="toolbar">
        <div className="tabs">
          {[['HOY', 'Hoy'], ['PENDIENTES', 'Pendientes'], ['HISTORIAL', 'Historial']].map(([v, t]) => (
            <button key={v} className={pestana === v ? 'active' : ''} onClick={() => setPestana(v)}>{t}</button>
          ))}
        </div>
      </div>

      {pestana === 'HOY' && <TabHoy resumen={resumen} cargando={cargando} recargar={recargar} />}
      {pestana === 'PENDIENTES' && <TabPendientes />}
      {pestana === 'HISTORIAL' && <TabHistorial />}
    </div>
  );
}

// ---------------- Hoy ----------------

/** Lo que se ve al abrir la pantalla: la caja abierta (o el cartel para abrirla) y el dia. */
function TabHoy({ resumen, cargando, recargar }) {
  const [cerrando, setCerrando] = useState(false);
  const { ejecutar, enviando } = useAccion();
  const caja = resumen?.caja_abierta;

  const abrir = async (datos) => {
    const r = await ejecutar(() => abrirCaja(datos), 'Caja abierta');
    if (r) recargar();
  };

  const cerrar = async (datos) => {
    const r = await ejecutar(() => cerrarCaja(caja.id, datos), 'Caja cerrada');
    if (r) { setCerrando(false); recargar(); }
  };

  if (cargando && !resumen) return <Cargando />;

  if (!caja) {
    return (
      <div className="card caja-abrir">
        <h3 className="seccion-titulo">No hay ninguna caja abierta</h3>
        <p className="pick-sub">
          El dinero solo entra al cajon con una caja abierta: asi el arqueo del turno siempre cuadra.
        </p>
        <FormAbrir onAbrir={abrir} enviando={enviando} />
      </div>
    );
  }

  return (
    <>
      <div className="caja-stats">
        <StatCard label="Monto inicial" value={fmtSoles(caja.monto_inicial)} icono="💰" color="blue" />
        <StatCard label="Cobrado en esta caja" value={fmtSoles(resumen.total_caja)} icono="🧾" color="green" />
        <StatCard label="Total del dia" value={fmtSoles(resumen.total_dia)} icono="📅" color="amber" />
      </div>

      <div className="caja-desglose">
        <div className="card caja-bloque">
          <h3 className="seccion-titulo">Por metodo de pago (hoy)</h3>
          <Desglose datos={resumen.por_metodo} etiqueta={(k) => nombreMetodo[k] || k} />
        </div>
        <div className="card caja-bloque">
          <h3 className="seccion-titulo">Por tipo de cobro (hoy)</h3>
          <Desglose datos={resumen.por_tipo} etiqueta={(k) => (tipoMovimientoCaja[k] || [k])[1]} />
        </div>
      </div>

      <div className="card" style={{ padding: 18, marginTop: 16 }}>
        <dl className="detalle">
          <dt>Apertura</dt><dd className="mono">{fmtFechaHora(caja.fecha_apertura)}</dd>
          <dt>Abrio</dt><dd>{caja.nombre_usuario_apertura}</dd>
          <dt>Efectivo esperado</dt><dd><b>{fmtSoles(caja.esperado_efectivo)}</b></dd>
          <dt>Observaciones</dt><dd>{caja.observaciones || '—'}</dd>
        </dl>
        <div className="acciones" style={{ marginTop: 14 }}>
          <button className="btn-danger" onClick={() => setCerrando(true)} disabled={enviando}>Cerrar caja</button>
        </div>
      </div>

      <h3 className="seccion-titulo" style={{ marginTop: 26 }}>Movimientos de esta caja</h3>
      <TablaMovimientos movimientos={resumen.movimientos || []} />

      {cerrando && (
        <ModalCerrar caja={caja} onClose={() => setCerrando(false)} onCerrar={cerrar} enviando={enviando} />
      )}
    </>
  );
}

/** Abrir el cajon: se dice cuanto dinero hay dentro. */
function FormAbrir({ onAbrir, enviando }) {
  const [monto, setMonto] = useState('0.00');
  const [observaciones, setObservaciones] = useState('');

  return (
    <form
      className="form-grid"
      style={{ marginTop: 14 }}
      onSubmit={(e) => {
        e.preventDefault();
        onAbrir({ monto_inicial: Number(monto) || 0, observaciones });
      }}
    >
      <Campo label="Monto inicial (S/)" ayuda="El dinero que ya hay en el cajon al abrirlo.">
        <input
          type="number" step="0.01" min="0" value={monto}
          onChange={(e) => setMonto(e.target.value)} required
        />
      </Campo>
      <Campo label="Observaciones">
        <input
          value={observaciones} maxLength={300} placeholder="Opcional"
          onChange={(e) => setObservaciones(e.target.value)}
        />
      </Campo>
      <div style={{ gridColumn: 'span 2' }}>
        <button className="btn-primary" type="submit" disabled={enviando}>
          {enviando ? 'Abriendo...' : 'Abrir caja'}
        </button>
      </div>
    </form>
  );
}

/** Cerrar el cajon: se cuenta el efectivo y el sistema dice si sobra o falta. */
function ModalCerrar({ caja, onClose, onCerrar, enviando }) {
  const [monto, setMonto] = useState('');
  const [observaciones, setObservaciones] = useState('');

  const esperado = Number(caja.esperado_efectivo || 0);
  const contado = Number(monto || 0);
  const diferencia = Math.round((contado - esperado) * 100) / 100;

  return (
    <Modal
      titulo="Cerrar la caja"
      subtitulo="Cuenta el efectivo del cajon: el sistema compara lo contado con lo esperado."
      onClose={onClose}
      pie={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancelar</button>
          <button
            className="btn-primary" onClick={() => onCerrar({ monto_final_declarado: contado, observaciones })}
            disabled={enviando || monto === ''}
          >
            {enviando ? 'Cerrando...' : 'Cerrar caja'}
          </button>
        </>
      }
    >
      <dl className="detalle" style={{ marginBottom: 16 }}>
        <dt>Monto inicial</dt><dd>{fmtSoles(caja.monto_inicial)}</dd>
        <dt>Efectivo esperado</dt><dd><b>{fmtSoles(esperado)}</b></dd>
      </dl>
      <div className="form-grid">
        <Campo label="Efectivo contado (S/)">
          <input
            type="number" step="0.01" min="0" value={monto}
            onChange={(e) => setMonto(e.target.value)} required autoFocus
          />
        </Campo>
        <Campo label="Observaciones">
          <input
            value={observaciones} maxLength={300} placeholder="Opcional"
            onChange={(e) => setObservaciones(e.target.value)}
          />
        </Campo>
        {monto !== '' && (
          <div className="caja-diferencia" style={{ gridColumn: 'span 2' }}>
            {diferencia === 0
              ? 'El efectivo cuadra: no hay sobrante ni faltante.'
              : diferencia > 0
                ? `Sobrante de ${fmtSoles(diferencia)}.`
                : `Faltante de ${fmtSoles(Math.abs(diferencia))}.`}
          </div>
        )}
      </div>
    </Modal>
  );
}

/** El desglose por metodo o por tipo (los maps del backend llegan como objetos). */
function Desglose({ datos, etiqueta }) {
  const claves = Object.keys(datos || {});
  if (claves.length === 0) return <div className="pick-sub">Hoy todavia no se cobró nada.</div>;
  return (
    <div className="caja-lineas">
      {claves.map((k) => (
        <div className="caja-linea" key={k}>
          <span>{etiqueta(k)}</span>
          <b className="mono">{fmtSoles(datos[k])}</b>
        </div>
      ))}
    </div>
  );
}

/** Los movimientos de dinero de la caja. */
function TablaMovimientos({ movimientos }) {
  return (
    <div className="card table-card">
      <table>
        <thead>
          <tr><th>Fecha</th><th>Concepto</th><th>Tipo</th><th>Metodo</th><th>Monto</th><th>Cobro</th></tr>
        </thead>
        <tbody>
          {(movimientos.length === 0) && (
            <FilaEstado columnas={6} cargando={false} vacio="Todavia no hay movimientos en esta caja." />
          )}
          {movimientos.map((m) => (
            <tr key={m.id}>
              <td className="mono">{fmtFechaHora(m.fecha)}</td>
              <td>{m.descripcion}</td>
              <td>{(tipoMovimientoCaja[m.tipo] || [null, m.tipo])[1]}</td>
              <td>{nombreMetodo[m.metodo_pago] || m.metodo_pago}</td>
              <td className="mono">{fmtSoles(m.monto)}</td>
              <td>{m.nombre_usuario}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ---------------- Pendientes ----------------

/** "Que libros faltan": prestamos sin devolver, reservas y pedidos sin cobrar. */
function TabPendientes() {
  const { datos, cargando } = useApi(() => getCajaPendientes(), [], null);

  if (cargando && !datos) return <Cargando />;

  const prestamos = datos?.prestamos || [];
  const porRecoger = datos?.reservas_por_recoger || [];
  const enCola = datos?.reservas_en_cola || [];
  const pedidos = datos?.pedidos_virtuales || [];

  return (
    <>
      <h3 className="seccion-titulo">Prestamos sin devolver</h3>
      <div className="card table-card">
        <table>
          <thead>
            <tr><th>Lector</th><th>Libro</th><th>Tipo</th><th>Desde</th><th>Vence</th><th>A cobrar</th></tr>
          </thead>
          <tbody>
            {(prestamos.length === 0) && (
              <FilaEstado columnas={6} cargando={cargando} vacio="No hay prestamos pendientes." />
            )}
            {prestamos.map((p) => (
              <tr key={p.id_prestamo}>
                <td>{p.nombre_lector}</td>
                <td>{p.titulo}</td>
                <td>{p.tipo === 'ABIERTO' ? 'Abierto' : 'Fecha fija'}</td>
                <td className="mono">{fmtFechaHora(p.fecha_prestamo)}</td>
                <td className="mono">
                  {p.fecha_vencimiento ? fmtFecha(p.fecha_vencimiento) : 'Sin fecha'}
                  {p.dias_de_retraso > 0 && <div className="pick-sub">{p.dias_de_retraso} dia(s) de retraso</div>}
                </td>
                <td className="mono">{p.precio_a_cobrar != null ? fmtSoles(p.precio_a_cobrar) : 'Sin precio'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h3 className="seccion-titulo" style={{ marginTop: 26 }}>Reservas por recoger</h3>
      <div className="card table-card">
        <table>
          <thead>
            <tr><th>Lector</th><th>Libro</th><th>Reservado</th><th>Limite de retiro</th><th>Faltan</th></tr>
          </thead>
          <tbody>
            {(porRecoger.length === 0) && (
              <FilaEstado columnas={5} cargando={cargando} vacio="No hay reservas por recoger." />
            )}
            {porRecoger.map((r) => (
              <tr key={r.id_reserva}>
                <td>{r.nombre_lector}</td>
                <td>{r.titulo}</td>
                <td className="mono">{fmtFechaHora(r.fecha_reserva)}</td>
                <td className="mono">{fmtFecha(r.fecha_limite_retiro)}</td>
                <td className="mono">
                  {r.dias_para_recoger != null ? `${r.dias_para_recoger} dia(s)` : '—'}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h3 className="seccion-titulo" style={{ marginTop: 26 }}>Reservas en cola</h3>
      <div className="card table-card">
        <table>
          <thead><tr><th>Lector</th><th>Libro</th><th>Reservado</th></tr></thead>
          <tbody>
            {(enCola.length === 0) && (
              <FilaEstado columnas={3} cargando={cargando} vacio="No hay reservas en cola." />
            )}
            {enCola.map((r) => (
              <tr key={r.id_reserva}>
                <td>{r.nombre_lector}</td>
                <td>{r.titulo}</td>
                <td className="mono">{fmtFechaHora(r.fecha_reserva)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <h3 className="seccion-titulo" style={{ marginTop: 26 }}>Pedidos de libro virtual sin cobrar</h3>
      <div className="card table-card">
        <table>
          <thead>
            <tr><th>Cliente</th><th>Libro</th><th>Tipo</th><th>Pago</th><th>Monto</th><th>Fecha</th></tr>
          </thead>
          <tbody>
            {(pedidos.length === 0) && (
              <FilaEstado columnas={6} cargando={cargando} vacio="No hay pedidos sin cobrar." />
            )}
            {pedidos.map((o) => (
              <tr key={o.id}>
                <td>{o.nombre_usuario}</td>
                <td>{o.titulo}</td>
                <td><Estado valor={o.tipo} mapa={tipoAccesoVirtual} /></td>
                <td>
                  {metodoPagoVirtual[o.metodo_pago] || o.metodo_pago || '—'}
                  {o.estado_comprobante && (
                    <div style={{ marginTop: 4 }}>
                      <Estado valor={o.estado_comprobante} mapa={estadoComprobante} />
                    </div>
                  )}
                </td>
                <td className="mono">{fmtSoles(o.precio)}</td>
                <td className="mono">{fmtFechaHora(o.fecha_creacion)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

// ---------------- Historial ----------------

/** Las cajas ya cerradas (y la que sigue abierta), con su arqueo. */
function TabHistorial() {
  const [desde, setDesde] = useState(inicioMesISO());
  const [hasta, setHasta] = useState(hoyISO());
  const { datos: cajas, cargando } = useApi(() => getCajas(desde, hasta), [desde, hasta], []);
  const [detalle, setDetalle] = useState(null);

  return (
    <>
      <div className="card" style={{ padding: 16, marginBottom: 16 }}>
        <div className="filtros-row">
          <div><label>Desde</label><input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} /></div>
          <div><label>Hasta</label><input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} /></div>
        </div>
      </div>

      <div className="card table-card">
        <table>
          <thead>
            <tr><th>Caja</th><th>Apertura</th><th>Cierre</th><th>Abrio</th><th>Cerró</th><th>Inicial</th><th>Cobrado</th><th>Esperado</th><th>Diferencia</th><th>Estado</th><th></th></tr>
          </thead>
          <tbody>
            {(cargando || cajas.length === 0) && (
              <FilaEstado columnas={11} cargando={cargando} vacio="No hay cajas en este rango." />
            )}
            {!cargando && cajas.map((c) => (
              <tr key={c.id}>
                <td className="mono">C{String(c.id).padStart(3, '0')}</td>
                <td className="mono">{fmtFechaHora(c.fecha_apertura)}</td>
                <td className="mono">{c.fecha_cierre ? fmtFechaHora(c.fecha_cierre) : '—'}</td>
                <td>{c.nombre_usuario_apertura}</td>
                <td>{c.nombre_usuario_cierre || '—'}</td>
                <td className="mono">{fmtSoles(c.monto_inicial)}</td>
                <td className="mono">{fmtSoles(c.total_cobrado)}</td>
                <td className="mono">{fmtSoles(c.esperado_efectivo)}</td>
                <td className="mono">
                  {c.diferencia != null ? fmtSoles(c.diferencia) : '—'}
                  {c.diferencia != null && Number(c.diferencia) !== 0 && (
                    <div className="pick-sub">{Number(c.diferencia) > 0 ? 'Sobrante' : 'Faltante'}</div>
                  )}
                </td>
                <td><Estado valor={c.estado} mapa={estadoCaja} /></td>
                <td><button className="btn-ghost btn-sm" onClick={() => setDetalle(c.id)}>Ver</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {detalle && <ModalDetalle idCaja={detalle} onClose={() => setDetalle(null)} />}
    </>
  );
}

/** El detalle de una caja: sus movimientos. */
function ModalDetalle({ idCaja, onClose }) {
  const { datos, cargando } = useApi(() => getCaja(idCaja), [idCaja], null);
  const caja = datos?.caja;

  return (
    <Modal
      titulo={`Caja C${String(idCaja).padStart(3, '0')}`}
      subtitulo={caja ? `Abrio ${caja.nombre_usuario_apertura} · ${fmtFechaHora(caja.fecha_apertura)}` : 'Cargando...'}
      onClose={onClose}
      ancho={760}
      pie={<button className="btn-primary" onClick={onClose}>Cerrar</button>}
    >
      {cargando && <Cargando />}
      {caja && (
        <>
          <dl className="detalle" style={{ marginBottom: 16 }}>
            <dt>Monto inicial</dt><dd>{fmtSoles(caja.monto_inicial)}</dd>
            <dt>Total cobrado</dt><dd>{fmtSoles(caja.total_cobrado)}</dd>
            <dt>Efectivo esperado</dt><dd>{fmtSoles(caja.esperado_efectivo)}</dd>
            <dt>Efectivo contado</dt><dd>{caja.monto_final_declarado != null ? fmtSoles(caja.monto_final_declarado) : '—'}</dd>
            <dt>Diferencia</dt>
            <dd>{caja.diferencia != null ? fmtSoles(caja.diferencia) : '—'}</dd>
            <dt>Estado</dt><dd><Estado valor={caja.estado} mapa={estadoCaja} /></dd>
          </dl>
          <TablaMovimientos movimientos={datos.movimientos || []} />
        </>
      )}
    </Modal>
  );
}

export default Caja;
