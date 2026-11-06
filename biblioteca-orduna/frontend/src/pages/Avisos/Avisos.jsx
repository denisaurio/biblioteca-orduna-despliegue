// Réplica del prototipo Figma "AVISOS Y NOTIFICACIONES" (RF11): listado cronológico,
// "+ Crear aviso" (bibliotecario/gerente) y "Configuración de avisos" (interruptores, solo gerente).
// El lector ve únicamente sus propios avisos.
import { useState } from 'react';
import { useAuth } from '../../context/AuthContext.jsx';
import {
  getMisNotificaciones, marcarLeida, marcarTodasLeidas, crearAviso, getTodosLosAvisos, generarAvisos,
} from '../../api/notificacionesApi.js';
import { getConfiguracion, actualizarConfiguracion } from '../../api/configuracionApi.js';
import { getUsuarios } from '../../api/usuariosApi.js';
import { useApi, useAccion, useDebounce, useRefrescoTiempoReal } from '../../hooks.js';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import Paginacion from '../../components/common/Paginacion.jsx';
import { Cargando, Vacio } from '../../components/common/Cargando.jsx';
import { tipoAviso } from '../../utils/estados.js';
import { fmtFechaHora } from '../../utils/formato.js';

const avisarCampana = () => window.dispatchEvent(new Event('avisos-actualizados'));

function Avisos() {
  const { esAdmin, esGerente } = useAuth();
  const [vista, setVista] = useState('mios');
  const [crear, setCrear] = useState(false);

  return (
    <div>
      <div className="page-head">
        <div><h1>Avisos y notificaciones</h1><p>Alertas automáticas del sistema (RF11).</p></div>
        {esAdmin && <button className="btn-primary" onClick={() => setCrear(true)}>+ Crear aviso</button>}
      </div>

      {esAdmin && (
        <div className="toolbar">
          <div className="tabs">
            <button className={vista === 'mios' ? 'active' : ''} onClick={() => setVista('mios')}>Mis avisos</button>
            <button className={vista === 'todos' ? 'active' : ''} onClick={() => setVista('todos')}>Todos los avisos</button>
          </div>
        </div>
      )}

      <div className="dos-columnas" style={{ gridTemplateColumns: esAdmin ? '1.6fr 1fr' : '1fr' }}>
        {vista === 'mios' ? <MisAvisos /> : <TodosLosAvisos version={crear} />}
        {esAdmin && <ConfigAvisos esGerente={esGerente} />}
      </div>

      {crear && <CrearAviso onClose={() => setCrear(false)} />}
    </div>
  );
}

function MisAvisos() {
  const { datos: avisos, setDatos, cargando, recargar } = useApi(() => getMisNotificaciones(), [], []);
  useRefrescoTiempoReal(['notificaciones'], recargar);
  const { ejecutar } = useAccion();

  const leer = async (a) => {
    if (a.leida) return;
    const r = await ejecutar(() => marcarLeida(a.id));
    if (r) { setDatos(avisos.map((x) => (x.id === a.id ? { ...x, leida: true } : x))); avisarCampana(); }
  };

  const leerTodas = async () => {
    const r = await ejecutar(() => marcarTodasLeidas(), 'Avisos marcados como leídos');
    if (r) { setDatos(avisos.map((x) => ({ ...x, leida: true }))); avisarCampana(); }
  };

  const sinLeer = avisos.filter((a) => !a.leida).length;

  return (
    <div className="card" style={{ padding: 8 }}>
      <div className="aviso-cabecera">
        <span>{sinLeer} sin leer</span>
        {sinLeer > 0 && <button className="btn-link" onClick={leerTodas}>Marcar todas como leídas</button>}
      </div>
      {cargando ? <Cargando /> : avisos.length === 0 ? <Vacio texto="No tienes avisos." /> : avisos.map((a) => (
        <ItemAviso key={a.id} aviso={a} onClick={() => leer(a)} />
      ))}
    </div>
  );
}

function ItemAviso({ aviso, onClick, conDestinatario }) {
  const [icono, titulo] = tipoAviso[aviso.tipo] || ['🔔', aviso.tipo];
  return (
    <div className={'aviso-item' + (aviso.leida ? ' leido' : '')} onClick={onClick} style={{ cursor: onClick ? 'pointer' : 'default' }}>
      <span className="aviso-icon">{icono}</span>
      <div style={{ flex: 1 }}>
        <div className="aviso-msg"><b>{titulo}</b>{!aviso.leida && <span className="punto-nuevo" />}</div>
        <div className="aviso-msg">{aviso.mensaje}</div>
        <div className="aviso-fecha">
          {fmtFechaHora(aviso.fecha_creacion)}
          {conDestinatario && ` · Para: ${aviso.nombre_destinatario}`}
          {aviso.creado_por && ` · Enviado por ${aviso.creado_por}`}
        </div>
      </div>
    </div>
  );
}

function TodosLosAvisos({ version }) {
  const [tipo, setTipo] = useState('');
  const [pagina, setPagina] = useState(0);
  const { datos, cargando, recargar } = useApi(() => getTodosLosAvisos({ tipo: tipo || undefined, pagina, tamanio: 15 }), [tipo, pagina, version]);
  useRefrescoTiempoReal(['notificaciones'], recargar);

  return (
    <div className="card" style={{ padding: 8 }}>
      <div className="aviso-cabecera">
        <select className="select-inline" value={tipo} onChange={(e) => { setTipo(e.target.value); setPagina(0); }}>
          <option value="">Todos los tipos</option>
          {Object.entries(tipoAviso).map(([v, [, t]]) => <option key={v} value={v}>{t}</option>)}
        </select>
      </div>
      {cargando ? <Cargando /> : !datos?.contenido.length ? <Vacio texto="No hay avisos." /> : (
        <>
          {datos.contenido.map((a) => <ItemAviso key={a.id} aviso={a} conDestinatario />)}
          <Paginacion pagina={datos.pagina} totalPaginas={datos.total_paginas} totalElementos={datos.total_elementos}
                      tamanio={datos.tamanio} onCambiar={setPagina} />
        </>
      )}
    </div>
  );
}

function ConfigAvisos({ esGerente }) {
  const { datos: config, setDatos, cargando, recargar } = useApi(() => getConfiguracion(), []);
  useRefrescoTiempoReal(['configuracion', 'notificaciones'], recargar);
  const { ejecutar, enviando } = useAccion();

  const cambiar = async (campo) => {
    const valor = !config[campo];
    const r = await ejecutar(() => actualizarConfiguracion({ [campo]: valor }), 'Configuración de avisos guardada');
    if (r) setDatos(r);
  };

  const generar = async () => {
    const r = await ejecutar(() => generarAvisos());
    if (r) window.alert(`Avisos generados: ${r.total} (próximos a vencer: ${r.vencimientos_proximos}, vencidos: ${r.prestamos_vencidos}, multas: ${r.multas_pendientes})`);
  };

  return (
    <div className="card" style={{ padding: 18 }}>
      <h3 className="seccion-titulo">Configuración de avisos</h3>
      {!esGerente && <p className="pick-sub" style={{ marginTop: -6 }}>Solo el gerente puede cambiar estos interruptores.</p>}
      {cargando || !config ? <Cargando /> : [
        ['aviso_vencimiento', 'Vencimiento de préstamo'],
        ['aviso_reserva', 'Reserva disponible'],
        ['aviso_multa', 'Multa generada'],
        ['aviso_nuevo_libro', 'Nuevo libro en catálogo'],
      ].map(([campo, label]) => (
        <label key={campo} className="switch-row">
          <span>{label}</span>
          <span className={'switch' + (config[campo] ? ' on' : '')}>
            <input type="checkbox" checked={!!config[campo]} disabled={!esGerente || enviando} onChange={() => cambiar(campo)} />
            <span className="switch-bola" />
          </span>
        </label>
      ))}
      <p className="pick-sub" style={{ marginTop: 12 }}>Los avisos de vencimiento se generan solos todos los días a las 8:00 a. m.</p>
      {esGerente && <button className="btn-ghost" style={{ marginTop: 6 }} onClick={generar} disabled={enviando}>Generar avisos ahora</button>}
    </div>
  );
}

function CrearAviso({ onClose }) {
  const [paraTodos, setParaTodos] = useState(true);
  const [destinatario, setDestinatario] = useState(null);
  const [busqueda, setBusqueda] = useState('');
  const [mensaje, setMensaje] = useState('');
  const q = useDebounce(busqueda);
  const { datos: encontrados } = useApi(() => getUsuarios({ q, tamanio: 6 }), [q], { contenido: [] });
  const { ejecutar, enviando } = useAccion();

  const enviar = async (e) => {
    e.preventDefault();
    const r = await ejecutar(() => crearAviso({ id_usuario: paraTodos ? null : destinatario?.id, mensaje }));
    if (r) {
      window.dispatchEvent(new Event('avisos-actualizados'));
      onClose();
      window.alert(`Aviso enviado a ${r.enviados} usuario(s).`);
    }
  };

  return (
    <Modal titulo="Crear aviso" subtitulo="Aviso administrativo para los lectores" onClose={onClose}
           pie={<><button className="btn-ghost" onClick={onClose}>Cancelar</button>
             <button className="btn-primary" form="form-aviso" disabled={enviando || (!paraTodos && !destinatario)}>Enviar aviso</button></>}>
      <form id="form-aviso" className="form-grid" onSubmit={enviar}>
        <Campo label="Destinatario" ancho={2}>
          <div className="tabs" style={{ alignSelf: 'flex-start' }}>
            <button type="button" className={paraTodos ? 'active' : ''} onClick={() => setParaTodos(true)}>Todos los lectores</button>
            <button type="button" className={!paraTodos ? 'active' : ''} onClick={() => setParaTodos(false)}>Un usuario</button>
          </div>
        </Campo>
        {!paraTodos && (
          <Campo label="Usuario" ancho={2}>
            {destinatario ? (
              <div style={{ display: 'flex', gap: 8 }}>
                <input value={`${destinatario.nombre} ${destinatario.apellido} · ${destinatario.email}`} disabled />
                <button type="button" className="btn-ghost" onClick={() => setDestinatario(null)}>Cambiar</button>
              </div>
            ) : (
              <>
                <input placeholder="Buscar por nombre, DNI o correo..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
                <div className="chips" style={{ marginTop: 6 }}>
                  {encontrados.contenido.map((u) => <span key={u.id} className="chip" onClick={() => setDestinatario(u)}>{u.nombre} {u.apellido}</span>)}
                </div>
              </>
            )}
          </Campo>
        )}
        <Campo label="Mensaje" ancho={2}>
          <textarea value={mensaje} onChange={(e) => setMensaje(e.target.value)} maxLength={500} required placeholder="Ej.: La biblioteca estará cerrada el lunes por feriado." />
        </Campo>
      </form>
    </Modal>
  );
}

export default Avisos;
