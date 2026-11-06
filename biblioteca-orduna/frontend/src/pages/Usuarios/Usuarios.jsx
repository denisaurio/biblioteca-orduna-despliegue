// Réplica del prototipo Figma "USUARIOS" (RF04): tabla con identificación, nombre, tipo,
// préstamos activos, estado y acciones (ver / editar), con paginación del backend.
import { useState } from 'react';
import { useAuth } from '../../context/AuthContext.jsx';
import { getUsuarios, crearUsuario, actualizarUsuario } from '../../api/usuariosApi.js';
import { getSancionesDeUsuario } from '../../api/sancionesApi.js';
import { useApi, useAccion, useDebounce, useRefrescoTiempoReal } from '../../hooks.js';
import SearchInput from '../../components/common/SearchInput.jsx';
import Paginacion from '../../components/common/Paginacion.jsx';
import Estado from '../../components/common/Estado.jsx';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import { FilaEstado, Cargando } from '../../components/common/Cargando.jsx';
import FormSancion from '../Sanciones/FormSancion.jsx';
import { estadoUsuario, estadoSancion } from '../../utils/estados.js';
import { codigo, fmtFecha, nombreRol, nombreTipoUsuario } from '../../utils/formato.js';

const TAMANIO = 8;

function Usuarios() {
  const { esGerente } = useAuth();
  const [busqueda, setBusqueda] = useState('');
  const [rol, setRol] = useState('');
  const [pagina, setPagina] = useState(0);
  const q = useDebounce(busqueda);
  const [ver, setVer] = useState(null);
  const [editar, setEditar] = useState(null);
  const [nuevo, setNuevo] = useState(false);

  const { datos, cargando, recargar } = useApi(
    () => getUsuarios({ q, rol: rol || undefined, pagina, tamanio: TAMANIO }),
    [q, rol, pagina]
  );
  useRefrescoTiempoReal(['usuarios', 'sanciones'], recargar);
  const lista = datos?.contenido || [];

  return (
    <div>
      <div className="page-head">
        <div><h1>Usuarios</h1><p>Gestión de usuarios del sistema (RF04)</p></div>
        <button className="btn-primary" onClick={() => setNuevo(true)}>+ Registrar usuario</button>
      </div>

      <div className="toolbar">
        <SearchInput placeholder="Buscar por nombre, DNI o correo..." value={busqueda} onChange={(e) => { setBusqueda(e.target.value); setPagina(0); }} />
        <select className="select-inline" value={rol} onChange={(e) => { setRol(e.target.value); setPagina(0); }}>
          <option value="">Todos los roles</option>
          <option value="LECTOR">Lectores</option>
          <option value="BIBLIOTECARIO">Bibliotecarios</option>
          <option value="GERENTE">Gerentes</option>
        </select>
      </div>

      <div className="card table-card">
        <table>
          <thead>
            <tr><th>Identificación</th><th>Nombre</th><th>DNI</th><th>Tipo</th><th>Préstamos</th><th>Estado</th><th>Acciones</th></tr>
          </thead>
          <tbody>
            {(cargando || lista.length === 0) && <FilaEstado columnas={7} cargando={cargando} vacio="No se encontraron usuarios." />}
            {!cargando && lista.map((u) => (
              <tr key={u.id}>
                <td className="mono">{codigo('U', u.id)}</td>
                <td>{u.nombre} {u.apellido}<div className="pick-sub">{u.email}</div></td>
                <td className="mono">{u.dni}</td>
                <td>{u.rol === 'LECTOR' ? nombreTipoUsuario[u.tipo_usuario] : nombreRol[u.rol]}</td>
                <td className="mono">{u.prestamos_activos}</td>
                <td><Estado valor={u.estado} mapa={estadoUsuario} /></td>
                <td>
                  <div className="acciones">
                    <button className="icon-btn" title="Ver" onClick={() => setVer(u)}>👁</button>
                    {(u.rol === 'LECTOR' || esGerente) && <button className="icon-btn" title="Editar" onClick={() => setEditar(u)}>✎</button>}
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
        {datos && (
          <Paginacion pagina={datos.pagina} totalPaginas={datos.total_paginas} totalElementos={datos.total_elementos}
                      tamanio={datos.tamanio} onCambiar={setPagina} />
        )}
      </div>

      {nuevo && <FormUsuario onClose={() => setNuevo(false)} onGuardado={() => { setNuevo(false); recargar(); }} />}
      {editar && <FormUsuario usuario={editar} onClose={() => setEditar(null)} onGuardado={() => { setEditar(null); recargar(); }} />}
      {ver && <VerUsuario usuario={ver} onClose={() => setVer(null)} onCambio={recargar} />}
    </div>
  );
}

/** Alta (crea LECTOR) o edición de usuario. */
export function FormUsuario({ usuario, onClose, onGuardado, guardar: guardarPropio }) {
  const editando = !!usuario;
  const [f, setF] = useState({
    nombre: usuario?.nombre || '', apellido: usuario?.apellido || '', dni: usuario?.dni || '', email: usuario?.email || '',
    telefono: usuario?.telefono || '', direccion: usuario?.direccion || '', tipo_usuario: usuario?.tipo_usuario || 'ESTUDIANTE',
    password: '', activo: usuario ? usuario.estado !== 'INACTIVO' : true,
  });
  const { ejecutar, enviando } = useAccion();
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value });

  const enviar = async (e) => {
    e.preventDefault();
    let peticion;
    if (editando) {
      const datos = { nombre: f.nombre, apellido: f.apellido, telefono: f.telefono, direccion: f.direccion,
        tipo_usuario: f.tipo_usuario, activo: f.activo, password: f.password || null };
      peticion = guardarPropio ? () => guardarPropio(datos) : () => actualizarUsuario(usuario.id, datos);
    } else {
      const { activo, ...datos } = f;
      peticion = () => crearUsuario(datos);
    }
    const ok = await ejecutar(peticion, editando ? 'Usuario actualizado' : 'Lector registrado');
    if (ok) onGuardado(ok);
  };

  return (
    <Modal
      titulo={editando ? 'Editar usuario' : 'Registrar usuario'}
      subtitulo={editando ? `${usuario.email} · DNI ${usuario.dni}` : 'Se crea una cuenta de lector. La contraseña se guarda cifrada.'}
      onClose={onClose}
      ancho={600}
      pie={<><button className="btn-ghost" onClick={onClose}>Cancelar</button><button className="btn-primary" form="form-usuario" disabled={enviando}>Guardar</button></>}
    >
      <form id="form-usuario" className="form-grid" onSubmit={enviar}>
        <Campo label="Nombre"><input value={f.nombre} onChange={set('nombre')} required /></Campo>
        <Campo label="Apellido"><input value={f.apellido} onChange={set('apellido')} required /></Campo>
        {!editando && <Campo label="DNI"><input value={f.dni} onChange={set('dni')} pattern="\d{8}" maxLength={8} title="8 dígitos" required /></Campo>}
        {!editando && <Campo label="Correo"><input type="email" value={f.email} onChange={set('email')} required /></Campo>}
        <Campo label="Teléfono"><input value={f.telefono} onChange={set('telefono')} /></Campo>
        <Campo label="Tipo de usuario">
          <select value={f.tipo_usuario} onChange={set('tipo_usuario')}>
            <option value="ESTUDIANTE">Estudiante</option><option value="DOCENTE">Docente</option><option value="EXTERNO">Externo</option>
          </select>
        </Campo>
        <Campo label="Dirección" ancho={2}><input value={f.direccion} onChange={set('direccion')} /></Campo>
        <Campo label={editando ? 'Nueva contraseña (opcional)' : 'Contraseña'} ancho={2}>
          <input type="password" value={f.password} onChange={set('password')} minLength={6} required={!editando} placeholder={editando ? 'Dejar vacío para no cambiarla' : ''} />
        </Campo>
        {editando && !guardarPropio && (
          <label className="checkbox-row" style={{ gridColumn: 'span 2', display: 'flex', gap: 8, fontSize: 13 }}>
            <input type="checkbox" checked={f.activo} onChange={set('activo')} /> Cuenta activa (desmarcar para desactivarla)
          </label>
        )}
      </form>
    </Modal>
  );
}

function VerUsuario({ usuario, onClose, onCambio }) {
  const [sancionar, setSancionar] = useState(false);
  const { datos: sanciones, cargando, recargar } = useApi(() => getSancionesDeUsuario(usuario.id), [usuario.id], []);

  return (
    <Modal
      titulo={`${usuario.nombre} ${usuario.apellido}`}
      subtitulo={`${codigo('U', usuario.id)} · ${nombreRol[usuario.rol]}`}
      onClose={onClose}
      ancho={600}
      pie={
        <>
          {usuario.rol === 'LECTOR' && <button className="btn-danger" style={{ marginRight: 'auto' }} onClick={() => setSancionar(true)}>Aplicar sanción</button>}
          <button className="btn-primary" onClick={onClose}>Cerrar</button>
        </>
      }
    >
      <dl className="detalle">
        <dt>DNI</dt><dd className="mono">{usuario.dni}</dd>
        <dt>Correo</dt><dd>{usuario.email}</dd>
        <dt>Teléfono</dt><dd>{usuario.telefono || '—'}</dd>
        <dt>Dirección</dt><dd>{usuario.direccion || '—'}</dd>
        <dt>Tipo</dt><dd>{nombreTipoUsuario[usuario.tipo_usuario]}</dd>
        <dt>Préstamos activos</dt><dd>{usuario.prestamos_activos}</dd>
        <dt>Registrado</dt><dd>{fmtFecha(usuario.fecha_registro)}</dd>
        <dt>Estado</dt><dd><Estado valor={usuario.estado} mapa={estadoUsuario} /></dd>
      </dl>
      <h3 className="seccion-titulo" style={{ marginTop: 20 }}>Historial de sanciones</h3>
      {cargando ? <Cargando /> : sanciones.length === 0 ? <p style={{ color: 'var(--muted)', fontSize: 13 }}>Sin sanciones.</p> : sanciones.map((s) => (
        <div key={s.id} className="summary-row">
          <span>{s.motivo} · hasta {fmtFecha(s.fecha_fin)}</span>
          <Estado valor={s.estado} mapa={estadoSancion} />
        </div>
      ))}
      {sancionar && (
        <FormSancion lector={usuario} onClose={() => setSancionar(false)} onGuardado={() => { setSancionar(false); recargar(); onCambio(); }} />
      )}
    </Modal>
  );
}

export default Usuarios;
