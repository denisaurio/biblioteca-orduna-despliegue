// La caja: el cajón de dinero de la biblioteca (solo bibliotecario y gerente).
//
// Es UNA SOLA caja para toda la biblioteca. Solo el ADMINISTRADOR la abre (con el dinero
// que ya hay en el cajón y el ENCARGADO del turno), cambia el encargado y la cierra. Solo
// el encargado (o el administrador) cobra; lo que presta otro bibliotecario queda "por
// cobrar" hasta que el encargado lo cobre. Al final el encargado cuenta el efectivo
// (arqueo) y el administrador cierra. El dinero solo entra por los movimientos.
//
// Las cajas se relacionan: un préstamo empieza en una caja (donde se pagó) y termina en
// la caja del día en que se devuelve; la multa queda en la caja donde se pagó.
//
// La caja NO es el turno (la bitacora del personal) ni el cierre diario: son tres cosas
// distintas que conviven.
import { useState } from 'react';
import {
  abrirCaja,
  arquearCaja,
  cambiarEncargadoCaja,
  cerrarCaja,
  getCaja,
  getCajaActual,
  getCajaPendientes,
  getCajas,
} from '../../api/cajaApi.js';
import { anularPrestamo, cobrarPrestamo } from '../../api/prestamosApi.js';
import { getUsuarios } from '../../api/usuariosApi.js';
import { useAuth } from '../../context/AuthContext.jsx';
import { getMiHorarioCaja } from '../../api/configuracionCajaApi.js';
import { useApi, useAccion, useRefrescoTiempoReal } from '../../hooks.js';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import Estado from '../../components/common/Estado.jsx';
import StatCard from '../../components/common/StatCard.jsx';
import { Cargando, FilaEstado } from '../../components/common/Cargando.jsx';
import {
  estadoCaja,
  estadoComprobante,
  estadoPrestamo,
  metodoPagoVirtual,
  metodosPago,
  nombreMetodo,
  nombreTipoPrestamo,
  tipoAccesoVirtual,
  tipoMovimientoCaja,
} from '../../utils/estados.js';
import { fmtFecha, fmtFechaHora, fmtSoles, hoyISO, inicioMesISO, nombreRol } from '../../utils/formato.js';
import './Caja.css';

function Caja() {
  const [pestana, setPestana] = useState('HOY');
  const { datos: resumen, cargando, recargar } = useApi(() => getCajaActual(), [], null);
  const { datos: miHorario } = useApi(() => getMiHorarioCaja(), [], null);
  useRefrescoTiempoReal(['cajas', 'prestamos', 'devoluciones', 'multas'], recargar);

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Caja</h1>
          <p>El cajon de la biblioteca: quien cobra, cuanto entro, por que metodo y que libros faltan.</p>
        </div>
      </div>

      <div className="toolbar">
        <div className="tabs">
          {[['HOY', 'Hoy'], ['PENDIENTES', 'Pendientes'], ['HISTORIAL', 'Historial']].map(([v, t]) => (
            <button key={v} className={pestana === v ? 'active' : ''} onClick={() => setPestana(v)}>{t}</button>
          ))}
        </div>
      </div>

      {pestana === 'HOY' && (
        <TabHoy resumen={resumen} cargando={cargando} recargar={recargar} miHorario={miHorario} />
      )}
      {pestana === 'PENDIENTES' && <TabPendientes />}
      {pestana === 'HISTORIAL' && <TabHistorial />}
    </div>
  );
}

// ---------------- Hoy ----------------

/** Bibliotecarios y gerentes activos: los que pueden ser encargados de la caja (solo lo pide el gerente). */
function usePersonal(activo) {
  const { datos } = useApi(async () => {
    if (!activo) return { data: [] };
    const [b, g] = await Promise.all([
      getUsuarios({ rol: 'BIBLIOTECARIO', tamanio: 100 }),
      getUsuarios({ rol: 'GERENTE', tamanio: 100 }),
    ]);
    const todos = [...(b.data?.contenido || []), ...(g.data?.contenido || [])];
    return { data: todos.filter((u) => u.estado !== 'INACTIVO') };
  }, [activo], []);
  return datos || [];
}

/** Lo que se ve al abrir la pantalla: la caja abierta (o el cartel para abrirla) y el dia. */
function TabHoy({ resumen, cargando, recargar, miHorario }) {
  const { esGerente } = useAuth();
  const personal = usePersonal(esGerente);
  const [modal, setModal] = useState(null); // 'CERRAR' | 'ARQUEO' | 'ENCARGADO'
  const { ejecutar, enviando } = useAccion();
  const caja = resumen?.caja_abierta;
  const puedoCobrar = resumen?.puedo_cobrar === true;
  const horarioHoy = miHorario?.horario_hoy;

  const abrir = async (datos) => {
    const r = await ejecutar(() => abrirCaja(datos), 'Caja abierta');
    if (r) recargar();
  };
  const terminar = (r) => { if (r) { setModal(null); recargar(); } };
  const cerrar = async (datos) => terminar(await ejecutar(() => cerrarCaja(caja.id, datos), 'Caja cerrada'));
  const arquear = async (datos) => terminar(await ejecutar(() => arquearCaja(caja.id, datos), 'Arqueo registrado'));
  const cambiarEncargado = async (id) =>
    terminar(await ejecutar(() => cambiarEncargadoCaja(caja.id, id), 'Encargado actualizado'));

  if (cargando && !resumen) return <Cargando />;

  // El gerente configura el horario; aquí se ve a qué hora se cierra la caja sola.
  const avisoHorario = (
    <div className="caja-aviso-horario">
      {horarioHoy ? (
        <>
          Hoy la caja la atiende <b>{horarioHoy.nombre_encargado || '—'}</b> y se cierra sola a las{' '}
          <b className="mono">{String(horarioHoy.hora_fin || '').slice(0, 5)}</b>.
          {caja && esGerente && ' Puedes cerrarla antes con «Cerrar caja».'}
        </>
      ) : (
        'Hoy no hay una caja programada a tu nombre: no se cierra automáticamente.'
      )}
    </div>
  );

  if (!caja) {
    return (
      <div className="card caja-abrir">
        <h3 className="seccion-titulo">No hay ninguna caja abierta</h3>
        <p className="pick-sub">
          Sin caja abierta no se presta, no se devuelve ni se cobra: asi cada prestamo queda en la caja
          donde empezo y en la caja donde termino.
        </p>
        {avisoHorario}
        {esGerente
          ? <FormAbrir onAbrir={abrir} enviando={enviando} personal={personal} />
          : (
            <div className="alert-box" style={{ marginTop: 14 }}>
              <span className="alert-icon">🔒</span>
              <div><b>Solo el administrador abre la caja</b>
                <div className="alert-sub">Pidele que la abra y que elija al encargado del turno.</div></div>
            </div>
          )}
      </div>
    );
  }

  const porCobrar = resumen.por_cobrar || [];

  return (
    <>
      <div className={`caja-encargado ${puedoCobrar ? 'mio' : ''}`}>
        <div>
          <div className="caja-encargado-titulo">Caja C{String(caja.id).padStart(3, '0')} · Encargado: <b>{caja.nombre_encargado || 'sin asignar'}</b></div>
          <div className="pick-sub">
            {puedoCobrar
              ? (esGerente ? 'Como administrador puedes cobrar, cambiar el encargado y cerrar la caja.'
                : 'Eres el encargado: tu cobras los prestamos, las multas y los libros virtuales.')
              : `Solo ${caja.nombre_encargado || 'el encargado'} o el administrador cobran. Tus prestamos quedan "por cobrar".`}
          </div>
        </div>
        <div className="acciones">
          {puedoCobrar && (
            <button className="btn-ghost" onClick={() => setModal('ARQUEO')} disabled={enviando}>Contar efectivo</button>
          )}
          {esGerente && (
            <>
              <button className="btn-ghost" onClick={() => setModal('ENCARGADO')} disabled={enviando}>Cambiar encargado</button>
              <button className="btn-danger" onClick={() => setModal('CERRAR')} disabled={enviando}>Cerrar caja</button>
            </>
          )}
        </div>
      </div>

      {avisoHorario}
      <div className="caja-stats">
        <StatCard label="Monto inicial" value={fmtSoles(caja.monto_inicial)} icono="💰" color="blue" />
        <StatCard label="Cobrado en esta caja" value={fmtSoles(resumen.total_caja)} icono="🧾" color="green" />
        <StatCard label="Por cobrar" value={porCobrar.length} icono="⏳" color="amber" />
        <StatCard label="Total del dia" value={fmtSoles(resumen.total_dia)} icono="📅" color="amber" />
      </div>

      <TablaPorCobrar prestamos={porCobrar} puedoCobrar={puedoCobrar} recargar={recargar} />

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
          <dt>Arqueo del encargado</dt>
          <dd>
            {caja.monto_contado_encargado != null
              ? <>{fmtSoles(caja.monto_contado_encargado)} <span className="pick-sub">· {caja.nombre_usuario_arqueo}, {fmtFechaHora(caja.fecha_arqueo)}</span></>
              : 'Todavia no conto el efectivo'}
          </dd>
          <dt>Observaciones</dt><dd>{caja.observaciones || '—'}</dd>
        </dl>
        <HistorialEncargados encargados={caja.encargados} />
      </div>

      <h3 className="seccion-titulo" style={{ marginTop: 26 }}>Movimientos de esta caja</h3>
      <TablaMovimientos movimientos={resumen.movimientos || []} />

      {modal === 'CERRAR' && (
        <ModalCerrar caja={caja} onClose={() => setModal(null)} onCerrar={cerrar} enviando={enviando} />
      )}
      {modal === 'ARQUEO' && (
        <ModalArqueo caja={caja} onClose={() => setModal(null)} onArquear={arquear} enviando={enviando} />
      )}
      {modal === 'ENCARGADO' && (
        <ModalEncargado
          caja={caja} personal={personal} onClose={() => setModal(null)}
          onCambiar={cambiarEncargado} enviando={enviando}
        />
      )}
    </>
  );
}

/**
 * Prestamos que registro un bibliotecario que no es el encargado: el libro todavia no salio.
 * El encargado los cobra (y recien ahi empiezan) o se anulan si el cliente no paga.
 */
function TablaPorCobrar({ prestamos, puedoCobrar, recargar }) {
  const { ejecutar, enviando } = useAccion();
  const [metodo, setMetodo] = useState({});
  if (prestamos.length === 0) return null;

  const cobrar = async (p) => {
    const r = await ejecutar(() => cobrarPrestamo(p.id, metodo[p.id] || 'EFECTIVO'), 'Prestamo cobrado: el libro ya puede salir');
    if (r) recargar();
  };
  const anular = async (p) => {
    if (!window.confirm(`¿Anular el prestamo de "${p.titulo_libro}" a ${p.nombre_lector}? La copia vuelve a quedar libre.`)) return;
    const r = await ejecutar(() => anularPrestamo(p.id, 'El cliente no pago'), 'Prestamo anulado');
    if (r) recargar();
  };

  return (
    <>
      <h3 className="seccion-titulo" style={{ marginTop: 6 }}>Prestamos por cobrar</h3>
      <p className="pick-sub" style={{ marginTop: -6, marginBottom: 10 }}>
        El cliente paga al prestar. Hasta que el encargado cobre, el libro no sale.
      </p>
      <div className="card table-card" style={{ marginBottom: 18 }}>
        <table>
          <thead>
            <tr><th>Lector</th><th>Libro</th><th>Tipo</th><th>Registro</th><th>Precio</th><th>Pago</th><th></th></tr>
          </thead>
          <tbody>
            {prestamos.map((p) => (
              <tr key={p.id}>
                <td>{p.nombre_lector}<div className="pick-sub">DNI {p.dni_lector}</div></td>
                <td>{p.titulo_libro}<div className="pick-sub mono">{p.codigo_inventario}</div></td>
                <td>{nombreTipoPrestamo[p.tipo_prestamo] || p.tipo_prestamo}<div className="pick-sub">{p.dias_prestamo} dia(s)</div></td>
                <td>{p.nombre_bibliotecario}<div className="pick-sub mono">{fmtFechaHora(p.fecha_prestamo)}</div></td>
                <td className="mono"><b>{fmtSoles(p.precio_copia)}</b></td>
                <td>
                  {puedoCobrar ? (
                    <select value={metodo[p.id] || 'EFECTIVO'} onChange={(e) => setMetodo({ ...metodo, [p.id]: e.target.value })}>
                      {metodosPago.map((m) => <option key={m} value={m}>{nombreMetodo[m]}</option>)}
                    </select>
                  ) : <span className="pick-sub">Lo cobra el encargado</span>}
                </td>
                <td>
                  <div className="acciones">
                    {puedoCobrar && <button className="btn-primary btn-sm" onClick={() => cobrar(p)} disabled={enviando}>Cobrar</button>}
                    <button className="btn-ghost btn-sm" onClick={() => anular(p)} disabled={enviando}>Anular</button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}

/** Quien fue encargado de la caja y desde/hasta que hora. */
function HistorialEncargados({ encargados }) {
  if (!encargados || encargados.length === 0) return null;
  return (
    <div style={{ marginTop: 14 }}>
      <div className="pick-sub" style={{ marginBottom: 6 }}><b>Encargados de esta caja</b></div>
      <div className="caja-lineas">
        {encargados.map((t, i) => (
          <div className="caja-linea" key={i}>
            <span>{t.nombre}{t.asignado_por && <span className="pick-sub"> · asignado por {t.asignado_por}</span>}</span>
            <span className="mono">{fmtFechaHora(t.desde)} → {t.hasta ? fmtFechaHora(t.hasta) : 'ahora'}</span>
          </div>
        ))}
      </div>
    </div>
  );
}

/** Selector de encargado: bibliotecarios y gerentes activos. */
function SelectEncargado({ personal, valor, onChange, excluir }) {
  return (
    <select value={valor} onChange={(e) => onChange(e.target.value)} required>
      <option value="">Elige al encargado...</option>
      {personal.filter((u) => u.id !== excluir).map((u) => (
        <option key={u.id} value={u.id}>{u.nombre} {u.apellido} · {nombreRol[u.rol] || u.rol}</option>
      ))}
    </select>
  );
}

/** Abrir el cajon (solo el administrador): dinero que hay dentro y encargado del turno. */
function FormAbrir({ onAbrir, enviando, personal }) {
  const [monto, setMonto] = useState('0.00');
  const [encargado, setEncargado] = useState('');
  const [observaciones, setObservaciones] = useState('');

  return (
    <form
      className="form-grid"
      style={{ marginTop: 14 }}
      onSubmit={(e) => {
        e.preventDefault();
        onAbrir({ monto_inicial: Number(monto) || 0, id_encargado: Number(encargado), observaciones });
      }}
    >
      <Campo label="Monto inicial (S/)" ayuda="El dinero que ya hay en el cajon al abrirlo.">
        <input
          type="number" step="0.01" min="0" value={monto}
          onChange={(e) => setMonto(e.target.value)} required
        />
      </Campo>
      <Campo label="Encargado de la caja" ayuda="El empleado que cobrara en esta caja. Puedes cambiarlo despues.">
        <SelectEncargado personal={personal} valor={encargado} onChange={setEncargado} />
      </Campo>
      <Campo label="Observaciones">
        <input
          value={observaciones} maxLength={300} placeholder="Opcional"
          onChange={(e) => setObservaciones(e.target.value)}
        />
      </Campo>
      <div style={{ gridColumn: 'span 2' }}>
        <button className="btn-primary" type="submit" disabled={enviando || !encargado}>
          {enviando ? 'Abriendo...' : 'Abrir caja'}
        </button>
      </div>
    </form>
  );
}

/** Texto de sobrante o faltante. */
function Diferencia({ contado, esperado }) {
  const diferencia = Math.round((contado - esperado) * 100) / 100;
  return (
    <div className="caja-diferencia" style={{ gridColumn: 'span 2' }}>
      {diferencia === 0
        ? 'El efectivo cuadra: no hay sobrante ni faltante.'
        : diferencia > 0
          ? `Sobrante de ${fmtSoles(diferencia)}.`
          : `Faltante de ${fmtSoles(Math.abs(diferencia))}.`}
    </div>
  );
}

/** Arqueo: el encargado cuenta el efectivo del cajon. La caja sigue abierta. */
function ModalArqueo({ caja, onClose, onArquear, enviando }) {
  const [monto, setMonto] = useState(caja.monto_contado_encargado ?? '');
  const [observaciones, setObservaciones] = useState('');
  const esperado = Number(caja.esperado_efectivo || 0);

  return (
    <Modal
      titulo="Contar el efectivo (arqueo)"
      subtitulo="Cuenta el dinero del cajon. El administrador cerrara la caja con este monto."
      onClose={onClose}
      pie={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancelar</button>
          <button
            className="btn-primary" disabled={enviando || monto === ''}
            onClick={() => onArquear({ monto_contado: Number(monto), observaciones })}
          >
            {enviando ? 'Guardando...' : 'Guardar arqueo'}
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
          <input type="number" step="0.01" min="0" value={monto} onChange={(e) => setMonto(e.target.value)} required autoFocus />
        </Campo>
        <Campo label="Observaciones">
          <input value={observaciones} maxLength={300} placeholder="Opcional" onChange={(e) => setObservaciones(e.target.value)} />
        </Campo>
        {monto !== '' && <Diferencia contado={Number(monto)} esperado={esperado} />}
      </div>
    </Modal>
  );
}

/** El administrador cambia el encargado sin cerrar la caja (cambio de turno). */
function ModalEncargado({ caja, personal, onClose, onCambiar, enviando }) {
  const [nuevo, setNuevo] = useState('');
  return (
    <Modal
      titulo="Cambiar el encargado"
      subtitulo={`Ahora: ${caja.nombre_encargado || 'sin asignar'}. El cambio queda en el historial de la caja.`}
      onClose={onClose}
      pie={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancelar</button>
          <button className="btn-primary" disabled={enviando || !nuevo} onClick={() => onCambiar(Number(nuevo))}>
            {enviando ? 'Guardando...' : 'Cambiar encargado'}
          </button>
        </>
      }
    >
      <Campo label="Nuevo encargado" ayuda="El arqueo del encargado anterior se borra: lo hace quien entrega la caja al final.">
        <SelectEncargado personal={personal} valor={nuevo} onChange={setNuevo} excluir={caja.id_encargado} />
      </Campo>
    </Modal>
  );
}

/**
 * Cerrar el cajon (solo el administrador). Por defecto usa el efectivo que conto el
 * encargado; el administrador puede escribir otro monto si lo vuelve a contar.
 */
function ModalCerrar({ caja, onClose, onCerrar, enviando }) {
  const delEncargado = caja.monto_contado_encargado;
  const [monto, setMonto] = useState(delEncargado ?? '');
  const [observaciones, setObservaciones] = useState('');

  const esperado = Number(caja.esperado_efectivo || 0);
  const contado = Number(monto || 0);

  return (
    <Modal
      titulo="Cerrar la caja"
      subtitulo="Se compara el efectivo contado con lo esperado."
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
        <dt>Conto el encargado</dt>
        <dd>{delEncargado != null ? `${fmtSoles(delEncargado)} (${caja.nombre_usuario_arqueo})` : 'Todavia no hizo el arqueo'}</dd>
      </dl>
      <div className="form-grid">
        <Campo label="Efectivo contado (S/)" ayuda={delEncargado != null ? 'Es el monto del encargado; cambialo si lo volviste a contar.' : undefined}>
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
        {monto !== '' && <Diferencia contado={contado} esperado={esperado} />}
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
  const { datos, cargando, recargar } = useApi(() => getCajaPendientes(), [], null);
  useRefrescoTiempoReal(['cajas', 'accesos-virtuales', 'comprobantes'], recargar);

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
            <tr><th>Lector</th><th>Libro</th><th>Tipo</th><th>Desde</th><th>Vence</th><th>A cobrar al devolver</th></tr>
          </thead>
          <tbody>
            {(prestamos.length === 0) && (
              <FilaEstado columnas={6} cargando={cargando} vacio="No hay prestamos pendientes." />
            )}
            {prestamos.map((p) => (
              <tr key={p.id_prestamo}>
                <td>{p.nombre_lector}</td>
                <td>{p.titulo}</td>
                <td>{nombreTipoPrestamo[p.tipo] || p.tipo}</td>
                <td className="mono">{fmtFechaHora(p.fecha_prestamo)}</td>
                <td className="mono">
                  {p.fecha_vencimiento ? fmtFecha(p.fecha_vencimiento) : 'Sin fecha'}
                  {p.dias_de_retraso > 0 && <div className="pick-sub">{p.dias_de_retraso} dia(s) de retraso</div>}
                </td>
                <td className="mono">{p.precio_a_cobrar != null ? fmtSoles(p.precio_a_cobrar) : <span className="pick-sub">Pagado al prestar</span>}</td>
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
  const { datos: cajas, cargando, recargar } = useApi(() => getCajas(desde, hasta), [desde, hasta], []);
  useRefrescoTiempoReal(['cajas'], recargar);
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
            <tr><th>Caja</th><th>Apertura</th><th>Cierre</th><th>Encargado</th><th>Abrio / cerró</th><th>Inicial</th><th>Cobrado</th><th>Esperado</th><th>Diferencia</th><th>Estado</th><th></th></tr>
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
                <td>
                  {(c.encargados || []).length > 1
                    ? c.encargados.map((t) => t.nombre).join(' → ')
                    : c.nombre_encargado || '—'}
                </td>
                <td>{c.nombre_usuario_apertura}<div className="pick-sub">{c.nombre_usuario_cierre || '—'}</div></td>
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
      subtitulo={caja ? `Abrio ${caja.nombre_usuario_apertura} · ${fmtFechaHora(caja.fecha_apertura)} · Encargado: ${caja.nombre_encargado || '—'}` : 'Cargando...'}
      onClose={onClose}
      ancho={900}
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
          <HistorialEncargados encargados={caja.encargados} />
          <h3 className="seccion-titulo" style={{ marginTop: 18 }}>Movimientos</h3>
          <TablaMovimientos movimientos={datos.movimientos || []} />
          <h3 className="seccion-titulo" style={{ marginTop: 18 }}>Prestamos que empezaron en esta caja</h3>
          <TablaPrestamosCaja prestamos={datos.prestamos_iniciados || []} idCaja={idCaja} vacio="Ningun prestamo empezo en esta caja." />
          <h3 className="seccion-titulo" style={{ marginTop: 18 }}>Prestamos devueltos en esta caja</h3>
          <TablaPrestamosCaja prestamos={datos.prestamos_finalizados || []} idCaja={idCaja} vacio="No se devolvio ningun prestamo en esta caja." />
        </>
      )}
    </Modal>
  );
}

/** Código corto de una caja: C003. */
const codCaja = (id) => (id ? `C${String(id).padStart(3, '0')}` : '—');

/**
 * Prestamos de una caja con su recorrido: en que caja empezo (y se pago) y en cual
 * termino. Asi se ve que un prestamo de la caja C001 se devolvio en la C003.
 */
function TablaPrestamosCaja({ prestamos, idCaja, vacio }) {
  return (
    <div className="card table-card">
      <table>
        <thead>
          <tr><th>Lector</th><th>Libro</th><th>Prestado</th><th>Devuelto</th><th>Recorrido</th><th>Estado</th></tr>
        </thead>
        <tbody>
          {prestamos.length === 0 && <FilaEstado columnas={6} cargando={false} vacio={vacio} />}
          {prestamos.map((p) => (
            <tr key={p.id}>
              <td>{p.nombre_lector}</td>
              <td>{p.titulo_libro}</td>
              <td className="mono">{fmtFechaHora(p.fecha_prestamo)}</td>
              <td className="mono">{p.fecha_devolucion ? fmtFechaHora(p.fecha_devolucion) : '—'}</td>
              <td className="mono">
                <span className={p.id_caja_inicio === idCaja ? 'caja-actual' : ''}>{codCaja(p.id_caja_inicio)}</span>
                {' → '}
                <span className={p.id_caja_fin === idCaja ? 'caja-actual' : ''}>{p.id_caja_fin ? codCaja(p.id_caja_fin) : p.estado === 'ANULADO' ? 'no salió' : p.estado === 'POR_COBRAR' ? 'por cobrar' : 'fuera'}</span>
                {p.id_caja_cobro && <div className="pick-sub">Pagado en {codCaja(p.id_caja_cobro)}</div>}
              </td>
              <td><Estado valor={p.estado} mapa={estadoPrestamo} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export default Caja;
