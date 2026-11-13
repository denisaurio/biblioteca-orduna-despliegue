// Réplica del prototipo Figma "DEVOLUCIONES" (RF07, RF08): tabla de préstamos con fecha límite,
// estado En plazo / Retraso y alerta de préstamos vencidos. La multa la calcula el backend
// (días de retraso × tarifa vigente al prestar). Un préstamo abierto nunca genera multa.
// Al devolver se cobra el precio de la copia: ese cobro entra a la caja, así que hace
// falta que haya una caja abierta.
import { useState } from 'react';
import { getPrestamos } from '../../api/prestamosApi.js';
import { registrarDevolucion } from '../../api/devolucionesApi.js';
import { getCajaActual } from '../../api/cajaApi.js';
import { useApi, useAccion, useDebounce, useRefrescoTiempoReal } from '../../hooks.js';
import SearchInput from '../../components/common/SearchInput.jsx';
import Estado from '../../components/common/Estado.jsx';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import { FilaEstado } from '../../components/common/Cargando.jsx';
import { estadoPrestamo, nombreMetodo } from '../../utils/estados.js';
import { codigo, fmtFecha, fmtSoles } from '../../utils/formato.js';

/** Con qué se puede cobrar el precio de la copia al devolver. */
const METODOS_DEVOLUCION = ['EFECTIVO', 'YAPE', 'TARJETA', 'TRANSFERENCIA'];

/**
 * Cargo sugerido cuando el libro vuelve dañado o perdido. El bibliotecario puede
 * cambiarlo (o dejarlo en 0 para no cobrar). No se cobra al devolver: se suma a
 * la multa y se paga después en "Pagos de multas".
 *   50% del precio de la copia si está dañado · 100% si está perdido.
 */
function cargoSugerido(estado, precioCopia) {
  if (precioCopia == null) return '';
  const precio = Number(precioCopia);
  if (!Number.isFinite(precio) || precio <= 0) return '';
  if (estado === 'DANADO') return (precio * 0.5).toFixed(2);
  if (estado === 'PERDIDO') return precio.toFixed(2);
  return '';
}

function Devoluciones() {
  const [busqueda, setBusqueda] = useState('');
  const [filtro, setFiltro] = useState('ACTIVOS');
  const [registrar, setRegistrar] = useState(null);
  const q = useDebounce(busqueda);
  const { datos: prestamos, cargando, recargar } = useApi(() => getPrestamos(filtro, q), [filtro, q], []);
  const { datos: vencidos, recargar: recargarVencidos } = useApi(() => getPrestamos('VENCIDOS'), [], []);
  useRefrescoTiempoReal(['prestamos', 'devoluciones', 'multas'], () => {
    recargar({ silencioso: true });
    recargarVencidos({ silencioso: true });
  });

  return (
    <div>
      <div className="page-head">
        <div><h1>Devoluciones</h1><p>Gestiona las devoluciones y los préstamos vencidos.</p></div>
      </div>

      <div className="toolbar">
        <SearchInput placeholder="Buscar por lector, DNI, libro o ejemplar..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        <div className="tabs">
          {[['ACTIVOS', 'Pendientes'], ['VENCIDOS', 'Con retraso'], ['DEVUELTOS', 'Devueltos']].map(([v, t]) => (
            <button key={v} className={filtro === v ? 'active' : ''} onClick={() => setFiltro(v)}>{t}</button>
          ))}
        </div>
      </div>

      <div className="card table-card" style={{ marginBottom: 16 }}>
        <table>
          <thead>
            <tr><th>Identificación</th><th>Usuario</th><th>Libro</th><th>Ejemplar</th><th>Precio</th><th>Fecha límite</th><th>Estado</th><th>Acciones</th></tr>
          </thead>
          <tbody>
            {(cargando || prestamos.length === 0) && <FilaEstado columnas={8} cargando={cargando} vacio="No hay préstamos en esta vista." />}
            {!cargando && prestamos.map((p) => (
              <tr key={p.id}>
                <td className="mono">{codigo('P', p.id)}</td>
                <td>{p.nombre_lector}<div className="pick-sub">DNI {p.dni_lector}</div></td>
                <td>{p.titulo_libro}</td>
                <td className="mono">{p.codigo_inventario}</td>
                <td className="mono">{p.precio_copia != null ? fmtSoles(p.precio_copia) : '—'}</td>
                <td className="mono">{p.fecha_vencimiento ? fmtFecha(p.fecha_vencimiento) : 'Abierto'}</td>
                <td>
                  <Estado valor={p.estado} mapa={estadoPrestamo} />
                  {p.dias_retraso > 0 && <div className="pick-sub">{p.dias_retraso} día(s)</div>}
                </td>
                <td>{p.estado !== 'DEVUELTO' && <button className="btn-primary btn-sm" onClick={() => setRegistrar(p)}>Registrar</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {vencidos.length > 0 && (
        <div className="alert-box">
          <span className="alert-icon">⚠</span>
          <div>
            <b>Devolución con retraso</b>
            <div className="alert-sub">{vencidos.length} préstamo(s) vencido(s)</div>
          </div>
          {filtro !== 'VENCIDOS' && <button className="btn-ghost" style={{ marginLeft: 'auto' }} onClick={() => setFiltro('VENCIDOS')}>Ver vencidos</button>}
        </div>
      )}

      {registrar && (
        <RegistrarDevolucion
          prestamo={registrar}
          onClose={() => setRegistrar(null)}
          onHecho={() => { recargar(); recargarVencidos(); }}
        />
      )}
    </div>
  );
}

function RegistrarDevolucion({ prestamo, onClose, onHecho }) {
  const [observaciones, setObservaciones] = useState('');
  const [estadoEjemplar, setEstadoEjemplar] = useState('DISPONIBLE');
  const [cargoDanado, setCargoDanado] = useState('');
  const [metodo, setMetodo] = useState('EFECTIVO');
  const [resultado, setResultado] = useState(null);
  const { ejecutar, enviando } = useAccion();
  // El cobro del precio de la copia entra a la caja: sin caja abierta no se puede devolver.
  const { datos: caja } = useApi(() => getCajaActual(), [], null);

  const tienePrecio = prestamo.precio_copia != null;
  const hayCaja = !!caja?.caja_abierta;
  const sinCaja = tienePrecio && !hayCaja;

  const enviar = async (e) => {
    e.preventDefault();
    const r = await ejecutar(
      () => registrarDevolucion({
        id_prestamo: prestamo.id,
        observaciones,
        estado_ejemplar: estadoEjemplar,
        metodo_pago: metodo,
        cargo_danado: Number(cargoDanado) || 0,
      }),
      'Devolución registrada'
    );
    if (r) { setResultado(r); onHecho(); }
  };

  return (
    <Modal
      titulo={resultado ? 'Devolución registrada' : 'Registrar devolución'}
      subtitulo={`${codigo('P', prestamo.id)} · ${prestamo.titulo_libro} · ${prestamo.nombre_lector}`}
      onClose={onClose}
      pie={resultado
        ? <button className="btn-primary" onClick={onClose}>Listo</button>
        : <><button className="btn-ghost" onClick={onClose}>Cancelar</button>
            <button className="btn-primary" form="form-dev" disabled={enviando || sinCaja}>Confirmar devolución</button></>}
    >
      {resultado ? (
        <>
          {resultado.id_multa
            ? <div className="error-form">
                Se generó una multa de <b>{fmtSoles(resultado.monto_multa)}</b>
                {resultado.concepto_multa ? ` (${resultado.concepto_multa})` : ''}.
                Puede cobrarla en "Pagos de multas".
              </div>
            : <div className="ok-form">No se generó multa.</div>}
          {resultado.monto_cobrado != null && (
            <div className="info-box">
              Se cobró <b>{fmtSoles(resultado.monto_cobrado)}</b> por el precio de la copia ({nombreMetodo[resultado.metodo_pago_cobro] || resultado.metodo_pago_cobro}).
              La multa se cobra aparte.
            </div>
          )}
          {resultado.id_reserva_notificada && (
            <div className="info-box">El ejemplar quedó apartado para el siguiente lector de la cola de reservas y se le notificó.</div>
          )}
        </>
      ) : (
        <form id="form-dev" className="form-grid" onSubmit={enviar}>
          {prestamo.dias_retraso > 0
            ? <div className="error-form" style={{ gridColumn: 'span 2', margin: 0 }}>Este préstamo tiene {prestamo.dias_retraso} día(s) de retraso: se generará una multa automáticamente.</div>
            : estadoEjemplar !== 'DISPONIBLE'
              ? <div className="info-box" style={{ gridColumn: 'span 2' }}>El libro vuelve {estadoEjemplar === 'DANADO' ? 'dañado' : 'perdido'}: puedes agregar un cargo a la multa.</div>
              : <div className="info-box" style={{ gridColumn: 'span 2' }}>{prestamo.tipo_prestamo === 'ABIERTO' ? 'Préstamo abierto: no genera multa por retraso.' : 'Dentro del plazo: no se generará multa por retraso.'}</div>}
          {sinCaja && (
            <div className="error-form" style={{ gridColumn: 'span 2', margin: 0 }}>
              No hay ninguna caja abierta. Ábrela en la sección <b>Caja</b> para poder cobrar el préstamo.
            </div>
          )}
          <Campo label="Estado del libro al devolverlo" ancho={2}>
            <select
              value={estadoEjemplar}
              onChange={(e) => {
                const estado = e.target.value;
                setEstadoEjemplar(estado);
                setCargoDanado(cargoSugerido(estado, prestamo.precio_copia));
              }}
            >
              <option value="DISPONIBLE">En buen estado (vuelve a circulación)</option>
              <option value="DANADO">Dañado</option>
              <option value="PERDIDO">Perdido</option>
            </select>
          </Campo>
          {estadoEjemplar !== 'DISPONIBLE' && (
            <Campo
              label="Cargo por daño o pérdida (S/)"
              ayuda={prestamo.precio_copia != null
                ? `Sugerencia: ${estadoEjemplar === 'DANADO' ? '50%' : '100%'} del precio de la copia. Puede cambiarlo (0 = no cobrar).`
                : 'La copia no tiene precio registrado; escriba el monto o déjelo en 0.'}
            >
              <input
                type="number"
                step="0.01"
                min="0"
                placeholder="0.00"
                value={cargoDanado}
                onChange={(e) => setCargoDanado(e.target.value)}
              />
            </Campo>
          )}
          {estadoEjemplar !== 'DISPONIBLE' && (
            <div className="info-box" style={{ gridColumn: 'span 2', margin: 0 }}>
              El cargo se suma a la multa del lector. No se cobra ahora: se paga en "Pagos de multas".
            </div>
          )}
          {tienePrecio && (
            <Campo label="Forma de pago del préstamo" ayuda={`Se cobra ${fmtSoles(prestamo.precio_copia)}. La multa se cobra aparte.`}>
              <select value={metodo} onChange={(e) => setMetodo(e.target.value)}>
                {METODOS_DEVOLUCION.map((m) => <option key={m} value={m}>{nombreMetodo[m]}</option>)}
              </select>
            </Campo>
          )}
          <Campo label="Observaciones" ancho={2}>
            <textarea value={observaciones} onChange={(e) => setObservaciones(e.target.value)} maxLength={500} placeholder="Opcional" />
          </Campo>
        </form>
      )}
    </Modal>
  );
}

export default Devoluciones;
