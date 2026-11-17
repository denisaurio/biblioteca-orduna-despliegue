// CRM del administrador: Ficha 360 del cliente (solo gerente).
//
// A la izquierda se busca al lector; a la derecha, todo lo suyo en una pantalla:
// datos y resumen (cuánto pagó, cuánto debe, si devuelve a tiempo), sus préstamos con
// la caja donde empezaron y donde terminaron, sus multas con la caja donde las pagó,
// todos sus pagos, sus sanciones y las notas internas del personal (el cliente no las ve).
//
// Se puede abrir directo con /clientes?id=3 (por ejemplo, desde la pantalla Usuarios).
import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getUsuarios } from '../../api/usuariosApi.js';
import { borrarNotaCliente, crearNotaCliente, getFichaCliente } from '../../api/crmApi.js';
import { useApi, useAccion, useDebounce, useRefrescoTiempoReal } from '../../hooks.js';
import SearchInput from '../../components/common/SearchInput.jsx';
import Estado from '../../components/common/Estado.jsx';
import StatCard from '../../components/common/StatCard.jsx';
import { Cargando, FilaEstado, Vacio } from '../../components/common/Cargando.jsx';
import {
  estadoMulta,
  estadoPrestamo,
  estadoUsuario,
  nombreMetodo,
  nombreTipoPrestamo,
  tipoMovimientoCaja,
} from '../../utils/estados.js';
import { codCaja, codigo, fmtFecha, fmtFechaHora, fmtSoles, iniciales, nombreTipoUsuario } from '../../utils/formato.js';
import './Clientes.css';

function Clientes() {
  const [params, setParams] = useSearchParams();
  const idSel = params.get('id') ? Number(params.get('id')) : null;
  const [busqueda, setBusqueda] = useState('');
  const q = useDebounce(busqueda);
  const { datos: lectores, cargando } = useApi(
    () => getUsuarios({ q, rol: 'LECTOR', tamanio: 30 }), [q], { contenido: [] });

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Clientes · Ficha 360</h1>
          <p>Todo de un lector en una pantalla: préstamos con sus cajas, pagos, multas, sanciones y notas internas.</p>
        </div>
      </div>

      <div className="crm-grid">
        <div className="card crm-lista">
          <SearchInput placeholder="Nombre, DNI o correo..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
          <div className="pick-list" style={{ marginTop: 12 }}>
            {cargando ? <Cargando /> : lectores.contenido.length === 0 ? <Vacio texto="No se encontraron lectores." /> :
              lectores.contenido.map((u) => (
                <div
                  key={u.id}
                  className={'pick-item' + (idSel === u.id ? ' selected' : '')}
                  onClick={() => setParams({ id: String(u.id) })}
                >
                  <span className="pick-avatar">{iniciales(`${u.nombre} ${u.apellido}`)}</span>
                  <div>
                    <div className="pick-title">{u.nombre} {u.apellido}</div>
                    <div className="pick-sub">DNI {u.dni} · {u.prestamos_activos} préstamo(s) activo(s)</div>
                  </div>
                </div>
              ))}
          </div>
        </div>

        <div>
          {idSel ? <Ficha idCliente={idSel} /> : (
            <div className="card crm-vacio">
              <Vacio texto="Elige un cliente de la lista para ver su ficha." />
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

/** La Ficha 360 de un cliente. */
function Ficha({ idCliente }) {
  const { datos: f, cargando, recargar } = useApi(() => getFichaCliente(idCliente), [idCliente], null);
  useRefrescoTiempoReal(['prestamos', 'devoluciones', 'multas', 'cajas', 'sanciones', 'crm'], () => recargar({ silencioso: true }));
  const [pestana, setPestana] = useState('PRESTAMOS');

  if (cargando && !f) return <Cargando />;
  if (!f) return null;
  const c = f.cliente;
  const r = f.resumen;

  return (
    <>
      <div className="card crm-cabecera">
        <span className="pick-avatar crm-avatar">{iniciales(`${c.nombre} ${c.apellido}`)}</span>
        <div style={{ flex: 1 }}>
          <h2>{c.nombre} {c.apellido}</h2>
          <div className="pick-sub">
            {codigo('U', c.id)} · DNI {c.dni} · {nombreTipoUsuario[c.tipo_usuario] || c.tipo_usuario}
            {' · '}{c.email}{c.telefono ? ` · ${c.telefono}` : ''}
          </div>
          <div className="pick-sub">
            Cliente desde {fmtFecha(c.fecha_registro)} · Última actividad {r.ultima_actividad ? fmtFechaHora(r.ultima_actividad) : '—'}
          </div>
        </div>
        <Estado valor={c.estado} mapa={estadoUsuario} />
      </div>

      <div className="crm-stats">
        <StatCard icono="💰" color="green" label="Total pagado" value={fmtSoles(r.total_pagado)} />
        <StatCard icono="⚠" color={Number(r.deuda) > 0 ? 'red' : 'blue'} label="Deuda por multas" value={fmtSoles(r.deuda)} />
        <StatCard icono="⇄" color="blue" label="Préstamos activos" value={`${r.activos}${r.vencidos ? ` (${r.vencidos} con retraso)` : ''}`} />
        <StatCard icono="⏱" color="amber" label="Devuelve a tiempo" value={r.a_tiempo_pct != null ? `${r.a_tiempo_pct}%` : '—'} />
      </div>

      <div className="card crm-desglose">
        <div><span>Préstamos</span><b>{r.total_prestamos}</b></div>
        <div><span>Devueltos</span><b>{r.devueltos} ({r.devueltos_a_tiempo} a tiempo)</b></div>
        <div><span>Por cobrar</span><b>{r.por_cobrar}</b></div>
        <div><span>Pagado en préstamos</span><b>{fmtSoles(r.pagado_prestamos)}</b></div>
        <div><span>Pagado en multas</span><b>{fmtSoles(r.pagado_multas)}</b></div>
        <div><span>Libros virtuales</span><b>{fmtSoles(r.pagado_virtual)}</b></div>
        <div><span>Multas generadas</span><b>{fmtSoles(r.multas_total)}</b></div>
        <div><span>Sanción activa</span><b>{r.sancion_activa ? 'Sí' : 'No'}</b></div>
      </div>

      <div className="toolbar" style={{ marginTop: 16 }}>
        <div className="tabs">
          {[['PRESTAMOS', `Préstamos (${f.prestamos.length})`], ['PAGOS', `Pagos (${f.pagos.length})`],
            ['MULTAS', `Multas (${f.multas.length})`], ['SANCIONES', `Sanciones (${f.sanciones.length})`],
            ['NOTAS', `Notas (${f.notas.length})`]].map(([v, t]) => (
            <button key={v} className={pestana === v ? 'active' : ''} onClick={() => setPestana(v)}>{t}</button>
          ))}
        </div>
      </div>

      {pestana === 'PRESTAMOS' && <TablaPrestamos prestamos={f.prestamos} />}
      {pestana === 'PAGOS' && <TablaPagos pagos={f.pagos} />}
      {pestana === 'MULTAS' && <TablaMultas multas={f.multas} />}
      {pestana === 'SANCIONES' && <TablaSanciones sanciones={f.sanciones} />}
      {pestana === 'NOTAS' && <Notas idCliente={c.id} notas={f.notas} recargar={recargar} />}
    </>
  );
}

/** Préstamos con su recorrido: caja de inicio (donde pagó) → caja de fin (donde devolvió). */
function TablaPrestamos({ prestamos }) {
  return (
    <div className="card table-card">
      <table>
        <thead>
          <tr><th>Préstamo</th><th>Libro</th><th>Tipo</th><th>Prestado</th><th>Devolver hasta</th><th>Devuelto</th><th>Cajas</th><th>Estado</th></tr>
        </thead>
        <tbody>
          {prestamos.length === 0 && <FilaEstado columnas={8} cargando={false} vacio="Este cliente aún no tiene préstamos." />}
          {prestamos.map((p) => (
            <tr key={p.id}>
              <td className="mono">{codigo('P', p.id)}</td>
              <td>{p.titulo_libro}<div className="pick-sub mono">{p.codigo_inventario}</div></td>
              <td>
                {nombreTipoPrestamo[p.tipo_prestamo] || p.tipo_prestamo}
                <div className="pick-sub">{p.dias_prestamo ? `${p.dias_prestamo} día(s) · ` : ''}{fmtSoles(p.tarifa_multa_diaria)}/día</div>
              </td>
              <td className="mono">{fmtFechaHora(p.fecha_prestamo)}</td>
              <td className="mono">{p.fecha_vencimiento ? fmtFecha(p.fecha_vencimiento) : '—'}</td>
              <td className="mono">{p.fecha_devolucion ? fmtFechaHora(p.fecha_devolucion) : '—'}</td>
              <td className="mono">
                {codCaja(p.id_caja_inicio)} → {p.id_caja_fin ? codCaja(p.id_caja_fin) : (p.estado === 'ANULADO' ? 'no salió' : p.estado === 'POR_COBRAR' ? 'por cobrar' : 'fuera')}
                <div className="pick-sub">
                  {p.id_caja_cobro ? `Pagó ${fmtSoles(p.precio_copia)} en ${codCaja(p.id_caja_cobro)}`
                    : p.estado === 'ANULADO' ? 'No pagó (anulado)': p.estado === 'POR_COBRAR' ? 'Falta cobrar' : p.precio_copia != null ? 'Sin cobro registrado' : 'Sin precio'}
                </div>
              </td>
              <td>
                <Estado valor={p.estado} mapa={estadoPrestamo} />
                {p.dias_retraso > 0 && <div className="pick-sub">{p.dias_retraso} día(s) · {fmtSoles(p.multa_acumulada)}</div>}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Todo el dinero que entró a caja por este cliente. */
function TablaPagos({ pagos }) {
  return (
    <div className="card table-card">
      <table>
        <thead><tr><th>Fecha</th><th>Concepto</th><th>Tipo</th><th>Método</th><th>Monto</th><th>Caja</th><th>Cobró</th></tr></thead>
        <tbody>
          {pagos.length === 0 && <FilaEstado columnas={7} cargando={false} vacio="Este cliente aún no pagó nada." />}
          {pagos.map((m) => (
            <tr key={m.id}>
              <td className="mono">{fmtFechaHora(m.fecha)}</td>
              <td>{m.descripcion}</td>
              <td>{(tipoMovimientoCaja[m.tipo] || [null, m.tipo])[1]}</td>
              <td>{nombreMetodo[m.metodo_pago] || m.metodo_pago}</td>
              <td className="mono">
                {fmtSoles(m.monto)}
                {Number(m.descuento) > 0 && <div className="pick-sub">Desc. {fmtSoles(m.descuento)}</div>}
              </td>
              <td className="mono">{codCaja(m.id_caja)}</td>
              <td>{m.nombre_usuario}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Multas con su recorrido de cajas: dónde empezó el préstamo, dónde se devolvió y dónde se pagó. */
function TablaMultas({ multas }) {
  return (
    <div className="card table-card">
      <table>
        <thead><tr><th>Multa</th><th>Libro</th><th>Motivo</th><th>Monto</th><th>Saldo</th><th>Cajas</th><th>Estado</th></tr></thead>
        <tbody>
          {multas.length === 0 && <FilaEstado columnas={7} cargando={false} vacio="Este cliente no tiene multas." />}
          {multas.map((m) => (
            <tr key={m.id}>
              <td className="mono">{codigo('M', m.id)}<div className="pick-sub">{fmtFecha(m.fecha_generacion)}</div></td>
              <td>{m.titulo_libro}</td>
              <td>{m.concepto || 'Multa'}</td>
              <td className="mono">{fmtSoles(m.monto)}</td>
              <td className="mono">{fmtSoles(m.saldo)}</td>
              <td className="mono">
                Préstamo {codCaja(m.id_caja_prestamo)} → {codCaja(m.id_caja_devolucion)}
                <div className="pick-sub">{m.cajas_pago?.length ? `Pagada en ${m.cajas_pago.map(codCaja).join(', ')}` : 'Sin pagos'}</div>
              </td>
              <td><Estado valor={m.estado} mapa={estadoMulta} /></td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function TablaSanciones({ sanciones }) {
  return (
    <div className="card table-card">
      <table>
        <thead><tr><th>Desde</th><th>Hasta</th><th>Motivo</th><th>Aplicó</th><th>Estado</th></tr></thead>
        <tbody>
          {sanciones.length === 0 && <FilaEstado columnas={5} cargando={false} vacio="Este cliente no tiene sanciones." />}
          {sanciones.map((s) => (
            <tr key={s.id}>
              <td className="mono">{fmtFechaHora(s.fecha_inicio)}</td>
              <td className="mono">{fmtFecha(s.fecha_fin)}</td>
              <td>{s.motivo}</td>
              <td>{s.nombre_bibliotecario}</td>
              <td>{s.estado === 'ACTIVA' ? 'Activa' : 'Finalizada'}{s.fecha_levantamiento && <div className="pick-sub">Levantada {fmtFecha(s.fecha_levantamiento)}</div>}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

/** Notas internas del personal sobre el cliente. El cliente nunca las ve. */
function Notas({ idCliente, notas, recargar }) {
  const [texto, setTexto] = useState('');
  const { ejecutar, enviando } = useAccion();

  const guardar = async (e) => {
    e.preventDefault();
    const r = await ejecutar(() => crearNotaCliente(idCliente, texto.trim()), 'Nota guardada');
    if (r) { setTexto(''); recargar(); }
  };
  const borrar = async (n) => {
    if (!window.confirm('¿Borrar esta nota?')) return;
    const r = await ejecutar(() => borrarNotaCliente(n.id), 'Nota borrada');
    if (r) recargar();
  };

  return (
    <div className="card crm-notas">
      <form onSubmit={guardar}>
        <textarea
          value={texto} maxLength={1000} placeholder='Ej.: "Llamó, paga la multa el viernes". Solo la ve el personal.'
          onChange={(e) => setTexto(e.target.value)}
        />
        <button className="btn-primary" type="submit" disabled={enviando || !texto.trim()}>Guardar nota</button>
      </form>
      {notas.length === 0 && <Vacio texto="Todavía no hay notas sobre este cliente." />}
      {notas.map((n) => (
        <div key={n.id} className="crm-nota">
          <div className="crm-nota-texto">{n.texto}</div>
          <div className="pick-sub">
            {n.nombre_autor} · {fmtFechaHora(n.fecha)}
            <button className="btn-ghost btn-sm" style={{ marginLeft: 10 }} onClick={() => borrar(n)} disabled={enviando}>Borrar</button>
          </div>
        </div>
      ))}
    </div>
  );
}

export default Clientes;
