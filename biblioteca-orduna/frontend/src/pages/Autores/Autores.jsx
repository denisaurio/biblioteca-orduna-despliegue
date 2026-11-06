// Réplica del prototipo Figma "AUTORES" (RF03): lista con nacionalidad, "Ver libros" y "Editor".
// No se puede eliminar un autor con libros asociados (lo valida el backend).
import { useState } from 'react';
import { useAuth } from '../../context/AuthContext.jsx';
import { getAutores, crearAutor, actualizarAutor, eliminarAutor, getLibrosDeAutor } from '../../api/autoresApi.js';
import { useApi, useAccion, useRefrescoTiempoReal } from '../../hooks.js';
import SearchInput from '../../components/common/SearchInput.jsx';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import Estado from '../../components/common/Estado.jsx';
import { Cargando, Vacio } from '../../components/common/Cargando.jsx';
import { estadoLibro } from '../../utils/estados.js';
import { iniciales } from '../../utils/formato.js';

function Autores() {
  const { esAdmin } = useAuth();
  const [busqueda, setBusqueda] = useState('');
  const [form, setForm] = useState(null);       // null | 'nuevo' | autor
  const [verLibros, setVerLibros] = useState(null);
  const { datos: autores, cargando, recargar } = useApi(() => getAutores(), [], []);
  useRefrescoTiempoReal(['autores', 'libros'], recargar);

  const filtrados = autores.filter((a) =>
    `${a.nombre} ${a.nacionalidad || ''}`.toLowerCase().includes(busqueda.toLowerCase())
  );

  return (
    <div>
      <div className="page-head">
        <div><h1>Autores</h1><p>Autores del catálogo (RF03)</p></div>
        {esAdmin && <button className="btn-primary" onClick={() => setForm('nuevo')}>+ Nuevo autor</button>}
      </div>

      <div className="toolbar">
        <SearchInput placeholder="Buscar autores..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
      </div>

      <div className="card">
        {cargando ? <Cargando /> : filtrados.length === 0 ? <Vacio texto="No hay autores registrados." /> : filtrados.map((a) => (
          <div key={a.id} className="autor-fila">
            <span className="pick-avatar" style={{ background: 'var(--border)', color: 'var(--ink)' }}>{iniciales(a.nombre)}</span>
            <div style={{ flex: 1, minWidth: 0 }}>
              <div className="pick-title">{a.nombre}</div>
              <div className="pick-sub">{a.nacionalidad || 'Nacionalidad no registrada'} · {a.cantidad_libros} libro(s)</div>
            </div>
            <div className="acciones">
              <button className="btn-ghost" onClick={() => setVerLibros(a)}>Ver libros</button>
              {esAdmin && <button className="btn-ghost" onClick={() => setForm(a)}>Editor</button>}
            </div>
          </div>
        ))}
      </div>

      {form && <FormAutor autor={form === 'nuevo' ? null : form} onClose={() => setForm(null)} onGuardado={() => { setForm(null); recargar(); }} />}
      {verLibros && <LibrosDelAutor autor={verLibros} onClose={() => setVerLibros(null)} />}
    </div>
  );
}

function FormAutor({ autor, onClose, onGuardado }) {
  const [f, setF] = useState({ nombre: autor?.nombre || '', nacionalidad: autor?.nacionalidad || '', biografia: autor?.biografia || '' });
  const { ejecutar, enviando } = useAccion();
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const guardar = async (e) => {
    e.preventDefault();
    const ok = await ejecutar(() => (autor ? actualizarAutor(autor.id, f) : crearAutor(f)), autor ? 'Autor actualizado' : 'Autor registrado');
    if (ok) onGuardado();
  };

  const eliminar = async () => {
    if (!window.confirm(`¿Eliminar a ${autor.nombre}?`)) return;
    const ok = await ejecutar(() => eliminarAutor(autor.id), 'Autor eliminado');
    if (ok) onGuardado();
  };

  return (
    <Modal
      titulo={autor ? 'Editar autor' : 'Nuevo autor'}
      onClose={onClose}
      pie={
        <>
          {autor && <button className="btn-danger" style={{ marginRight: 'auto' }} onClick={eliminar} disabled={enviando}>Eliminar</button>}
          <button className="btn-ghost" onClick={onClose}>Cancelar</button>
          <button className="btn-primary" form="form-autor" disabled={enviando}>Guardar</button>
        </>
      }
    >
      <form id="form-autor" className="form-grid" onSubmit={guardar}>
        <Campo label="Nombre completo" ancho={2}><input value={f.nombre} onChange={set('nombre')} required /></Campo>
        <Campo label="Nacionalidad" ancho={2}><input value={f.nacionalidad} onChange={set('nacionalidad')} placeholder="Peruana, Colombia, ..." /></Campo>
        <Campo label="Biografía" ancho={2}><textarea value={f.biografia} onChange={set('biografia')} /></Campo>
      </form>
    </Modal>
  );
}

function LibrosDelAutor({ autor, onClose }) {
  const { datos: libros, cargando } = useApi(() => getLibrosDeAutor(autor.id), [autor.id], []);
  return (
    <Modal titulo={`Libros de ${autor.nombre}`} onClose={onClose}>
      {cargando ? <Cargando /> : libros.length === 0 ? <Vacio texto="Este autor aún no tiene libros." /> : libros.map((l) => (
        <div key={l.id} className="summary-row">
          <span style={{ color: 'var(--ink)' }}>{l.titulo} <small style={{ color: 'var(--muted)' }}>({l.anio_publicacion || 's/f'})</small></span>
          <Estado valor={l.estado} mapa={estadoLibro} />
        </div>
      ))}
    </Modal>
  );
}

export default Autores;
