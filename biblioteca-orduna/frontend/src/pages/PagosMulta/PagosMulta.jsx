// Réplica del prototipo Figma "PAGOS DE MULTA" (RF15): usuario, monto, método, fecha y estado,
// con "+ Registrar pago". Acepta pagos parciales; el bibliotecario que cobra sale del JWT.
import { useState } from 'react';
import { getMultas, pagarMulta, getPagos, getDevolucionesSinMulta, crearMulta } from '../../api/pagosMultaApi.js';
import { useApi, useAccion, useRefrescoTiempoReal } from '../../hooks.js';
import SearchInput from '../../components/common/SearchInput.jsx';
import Estado from '../../components/common/Estado.jsx';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import { FilaEstado } from '../../components/common/Cargando.jsx';
import { estadoMulta, metodosPago, nombreMetodo } from '../../utils/estados.js';
import { codCaja, codigo, fmtFecha, fmtFechaHora, fmtSoles, inicioMesISO, hoyISO } from '../../utils/formato.js';

function PagosMulta() {
  const [vista, setVista] = useState('PENDIENTES');
  const [busqueda, setBusqueda] = useState('');
  const [pagar, setPagar] = useState(null); // multa | 'elegir'
  const [generar, setGenerar] = useState(false);
  const [refresco, setRefresco] = useState(0);

  return (
    <div>
      <div className="page-head">
        <div><h1>Pagos de multas</h1><p>Cobro y generación de multas por retraso o daño (RF15).</p></div>
        <div style={{ display: 'flex', gap: 10, flexWrap: 'wrap' }}>
          <button className="btn-ghost" onClick={() => setGenerar(true)}>+ Registrar multa</button>
          <button className="btn-primary" onClick={() => setPagar('elegir')}>+ Registrar pago</button>
        </div>
      </div>

      <div className="toolbar">
        <SearchInput placeholder="Buscar usuario..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        <div className="tabs">
          {[['PENDIENTES', 'Por cobrar'], ['PAGADAS', 'Pagadas'], ['TODAS', 'Todas'], ['HISTORIAL', 'Historial de pagos']].map(([v, t]) => (
            <button key={v} className={vista === v ? 'active' : ''} onClick={() => setVista(v)}>{t}</button>
          ))}
        </div>
      </div>

      {vista === 'HISTORIAL'
        ? <HistorialPagos busqueda={busqueda} />
        : <TablaMultas filtro={vista} busqueda={busqueda} onPagar={setPagar} version={pagar} refresco={refresco} />}

      {pagar && <RegistrarPago multa={pagar === 'elegir' ? null : pagar} onClose={() => setPagar(null)} />}
      {generar && (
        <RegistrarMulta
          onClose={() => setGenerar(false)}
          onHecho={() => { setGenerar(false); setRefresco((n) => n + 1); }}
        />
      )}
    </div>
  );
}

function TablaMultas({ filtro, busqueda, onPagar, version, refresco }) {
  const { datos: multas, cargando, recargar } = useApi(
    () => getMultas(filtro),
    [filtro, version === null, refresco],
    []
  );
  useRefrescoTiempoReal(['multas', 'pagos', 'devoluciones', 'cajas'], recargar);
  const lista = multas.filter((m) => `${m.nombre_lector} ${m.dni_lector}`.toLowerCase().includes(busqueda.toLowerCase()));

  return (
    <div className="card table-card">
      <table>
        <thead><tr><th>Identificación</th><th>Usuario</th><th>Libro</th><th>Monto</th><th>Pagado</th><th>Último método</th><th>Fecha</th><th>Estado</th><th></th></tr></thead>
        <tbody>
          {(cargando || lista.length === 0) && <FilaEstado columnas={9} cargando={cargando} vacio="No hay multas en esta vista." />}
          {!cargando && lista.map((m) => (
            <tr key={m.id}>
              <td className="mono">{codigo('M', m.id)}</td>
              <td>{m.nombre_lector}<div className="pick-sub">DNI {m.dni_lector}</div></td>
              <td>
                {m.titulo_libro}
                <div className="pick-sub">
                  {m.concepto || (m.dias_retraso > 0 ? `${m.dias_retraso} día(s) de retraso` : 'Motivo no registrado')}
                </div>
              </td>
              <td className="mono">{fmtSoles(m.monto)}</td>
              <td className="mono">{fmtSoles(m.monto_pagado)}</td>
              <td>
                {m.ultimo_metodo_pago ? nombreMetodo[m.ultimo_metodo_pago] : '—'}
                {m.cajas_pago?.length > 0 && <div className="pick-sub">Pagada en {m.cajas_pago.map(codCaja).join(', ')}</div>}
                {m.id_caja_prestamo && <div className="pick-sub">Préstamo {codCaja(m.id_caja_prestamo)} → {codCaja(m.id_caja_devolucion)}</div>}
              </td>
              <td className="mono">{fmtFecha(m.fecha_ultimo_pago || m.fecha_generacion)}</td>
              <td><Estado valor={m.estado} mapa={estadoMulta} /></td>
              <td>{m.estado !== 'PAGADA' && <button className="btn-primary btn-sm" onClick={() => onPagar(m)}>Cobrar</button>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function HistorialPagos({ busqueda }) {
  const [desde, setDesde] = useState(inicioMesISO());
  const [hasta, setHasta] = useState(hoyISO());
  const { datos: pagos, cargando, recargar } = useApi(() => getPagos(desde, hasta), [desde, hasta], []);
  useRefrescoTiempoReal(['multas', 'pagos', 'cajas'], recargar);
  const lista = pagos.filter((p) => p.nombre_lector.toLowerCase().includes(busqueda.toLowerCase()));
  const total = lista.reduce((s, p) => s + Number(p.monto_pagado), 0);

  return (
    <>
      <div className="card" style={{ padding: 16, marginBottom: 16 }}>
        <div className="filtros-row">
          <div><label>Desde</label><input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} /></div>
          <div><label>Hasta</label><input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} /></div>
          <div><label>Total cobrado</label><b style={{ fontSize: 18, paddingTop: 6 }}>{fmtSoles(total)}</b></div>
        </div>
      </div>
      <div className="card table-card">
        <table>
          <thead><tr><th>Pago</th><th>Usuario</th><th>Multa</th><th>Monto</th><th>Método</th><th>Fecha</th><th>Estado de la multa</th></tr></thead>
          <tbody>
            {(cargando || lista.length === 0) && <FilaEstado columnas={7} cargando={cargando} vacio="No hay pagos en este rango." />}
            {!cargando && lista.map((p) => (
              <tr key={p.id}>
                <td className="mono">{codigo('P', p.id)}</td>
                <td>{p.nombre_lector}</td>
                <td className="mono">{codigo('M', p.id_multa)}</td>
                <td className="mono">{fmtSoles(p.monto_pagado)}{Number(p.descuento) > 0 && <div className="pick-sub">desc. −{fmtSoles(p.descuento)}</div>}</td>
                <td>{nombreMetodo[p.metodo_pago]}</td>
                <td className="mono">{fmtFechaHora(p.fecha_pago)}</td>
                <td><Estado valor={p.estado_multa} mapa={estadoMulta} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

function RegistrarPago({ multa: inicial, onClose }) {
  const [multa, setMulta] = useState(inicial);
  const [monto, setMonto] = useState(inicial ? Number(inicial.saldo).toFixed(2) : '');
  const [metodo, setMetodo] = useState('EFECTIVO');
  const { datos: pendientes } = useApi(() => (inicial ? Promise.resolve({ data: [] }) : getMultas('PENDIENTES')), [], []);
  const { ejecutar, enviando } = useAccion();

  // Pago en efectivo: 15% de descuento sobre el monto que se salda.
  const esEfectivo = metodo === 'EFECTIVO';
  const montoNum = Number(monto) || 0;
  const descuento = esEfectivo ? Math.round(montoNum * 0.15 * 100) / 100 : 0;
  const aCobrar = Math.round((montoNum - descuento) * 100) / 100;

  const elegir = (id) => {
    const m = pendientes.find((x) => String(x.id) === id) || null;
    setMulta(m);
    setMonto(m ? Number(m.saldo).toFixed(2) : '');
  };

  const guardar = async (e) => {
    e.preventDefault();
    const r = await ejecutar(
      () => pagarMulta(multa.id, { monto_pagado: Number(monto), metodo_pago: metodo }),
      'Pago registrado'
    );
    if (r) onClose();
  };

  return (
    <Modal titulo="Registrar pago" subtitulo="Puede registrar un pago parcial; la multa quedará como 'Pago parcial'." onClose={onClose}
           pie={<><button className="btn-ghost" onClick={onClose}>Cancelar</button>
             <button className="btn-primary" form="form-pago" disabled={enviando || !multa}>Registrar pago</button></>}>
      <form id="form-pago" className="form-grid" onSubmit={guardar}>
        {!inicial && (
          <Campo label="Multa a cobrar" ancho={2}>
            <select value={multa?.id || ''} onChange={(e) => elegir(e.target.value)} required>
              <option value="">Seleccione...</option>
              {pendientes.map((m) => <option key={m.id} value={m.id}>{m.nombre_lector} — {m.titulo_libro} — saldo {fmtSoles(m.saldo)}</option>)}
            </select>
          </Campo>
        )}
        {multa && (
          <dl className="detalle" style={{ gridColumn: 'span 2' }}>
            <dt>Lector</dt><dd>{multa.nombre_lector}</dd>
            <dt>Monto de la multa</dt><dd>{fmtSoles(multa.monto)}</dd>
            <dt>Ya pagado</dt><dd>{fmtSoles(multa.monto_pagado)}</dd>
            <dt>Saldo</dt><dd><b>{fmtSoles(multa.saldo)}</b></dd>
          </dl>
        )}
        <Campo label="Monto a saldar (S/)">
          <input type="number" step="0.01" min="0.01" max={multa ? multa.saldo : undefined} value={monto} onChange={(e) => setMonto(e.target.value)} required />
        </Campo>
        <Campo label="Método de pago">
          <select value={metodo} onChange={(e) => setMetodo(e.target.value)}>
            {metodosPago.map((m) => <option key={m} value={m}>{nombreMetodo[m]}</option>)}
          </select>
        </Campo>
        {esEfectivo && montoNum > 0 && (
          <dl className="detalle" style={{ gridColumn: 'span 2' }}>
            <dt>Descuento por efectivo (15%)</dt><dd>− {fmtSoles(descuento)}</dd>
            <dt>Efectivo a recibir</dt><dd><b>{fmtSoles(aCobrar)}</b></dd>
          </dl>
        )}
      </form>
    </Modal>
  );
}

/**
 * "+ Registrar multa": genera una multa sobre una devolución que quedó sin multa
 * (por ejemplo, un libro dañado devuelto antes de existir el cargo por daño).
 * Solo crea la deuda; el dinero se cobra con "+ Registrar pago".
 */
function RegistrarMulta({ onClose, onHecho }) {
  const { datos: devoluciones, cargando } = useApi(() => getDevolucionesSinMulta(), [], []);
  const [idDevolucion, setIdDevolucion] = useState('');
  const [monto, setMonto] = useState('');
  const [concepto, setConcepto] = useState('');
  const { ejecutar, enviando } = useAccion();
  const lista = devoluciones || [];
  const elegida = lista.find((d) => String(d.id_devolucion) === idDevolucion);

  const elegir = (id) => {
    setIdDevolucion(id);
    const d = lista.find((x) => String(x.id_devolucion) === id);
    // Sugerencia: días de retraso × la tarifa que se copió en el préstamo.
    const sugerido = d && d.dias_retraso > 0 && d.tarifa_multa_diaria != null
      ? (Number(d.dias_retraso) * Number(d.tarifa_multa_diaria)).toFixed(2)
      : '';
    setMonto(sugerido);
    setConcepto(d && d.dias_retraso > 0 ? `Retraso (${d.dias_retraso} día(s))` : '');
  };

  const guardar = async (e) => {
    e.preventDefault();
    const r = await ejecutar(
      () => crearMulta({
        id_devolucion: Number(idDevolucion),
        monto: Number(monto),
        concepto: concepto.trim() || null,
      }),
      'Multa registrada'
    );
    if (r) onHecho();
  };

  return (
    <Modal
      titulo="Registrar multa"
      subtitulo="Genera una multa para una devolución que quedó sin multa. El cobro se hace después con «Registrar pago»."
      onClose={onClose}
      pie={<><button className="btn-ghost" onClick={onClose}>Cancelar</button>
        <button className="btn-primary" form="form-multa" disabled={enviando || !idDevolucion}>
          {enviando ? 'Registrando...' : 'Registrar multa'}
        </button></>}
    >
      <form id="form-multa" className="form-grid" onSubmit={guardar}>
        <Campo label="Devolución" ancho={2}>
          <select value={idDevolucion} onChange={(e) => elegir(e.target.value)} required>
            <option value="">{cargando ? 'Cargando...' : 'Seleccione una devolución...'}</option>
            {lista.map((d) => (
              <option key={d.id_devolucion} value={d.id_devolucion}>
                {codigo('P', d.id_prestamo)} — {d.nombre_lector} — {d.titulo_libro}
                {d.dias_retraso > 0 ? ` (${d.dias_retraso} día(s) de retraso)` : ''}
              </option>
            ))}
          </select>
        </Campo>
        {!cargando && lista.length === 0 && (
          <div className="info-box" style={{ gridColumn: 'span 2', margin: 0 }}>
            Todas las devoluciones registradas ya tienen multa.
          </div>
        )}
        <Campo label="Monto de la multa (S/)">
          <input type="number" step="0.01" min="0.01" value={monto}
                 onChange={(e) => setMonto(e.target.value)} required />
        </Campo>
        <Campo label="Motivo" ayuda="Ej.: libro dañado, libro perdido, otro cargo aprobado.">
          <input value={concepto} onChange={(e) => setConcepto(e.target.value)}
                 maxLength={120} placeholder="Multa manual" />
        </Campo>
        {elegida && (
          <dl className="detalle" style={{ gridColumn: 'span 2' }}>
            <dt>Lector</dt><dd>{elegida.nombre_lector} · DNI {elegida.dni_lector}</dd>
            <dt>Libro</dt><dd>{elegida.titulo_libro} ({elegida.codigo_inventario})</dd>
            <dt>Devuelto</dt><dd>{fmtFechaHora(elegida.fecha_devolucion)}</dd>
            <dt>Días de retraso</dt><dd>{elegida.dias_retraso}</dd>
            <dt>Precio de la copia</dt><dd>{elegida.precio_copia != null ? fmtSoles(elegida.precio_copia) : '—'}</dd>
          </dl>
        )}
      </form>
    </Modal>
  );
}

export default PagosMulta;
