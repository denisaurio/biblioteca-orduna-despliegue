// Réplica del prototipo Figma "EJEMPLARES" (RF14): identificación, libro, estado, ubicación,
// "Ver detalle" y "+ Registrar ejemplar". PRESTADO y RESERVADO los pone el sistema;
// a mano solo: Disponible, Dañado, Perdido o De baja.
// Cada copia tiene su propio precio: es el que se copia al préstamo cuando se presta.
import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { getEjemplares, crearEjemplar, actualizarEjemplar } from '../../api/ejemplaresApi.js';
import { getLibros } from '../../api/librosApi.js';
import { useApi, useAccion, useDebounce } from '../../hooks.js';
import SearchInput from '../../components/common/SearchInput.jsx';
import Estado from '../../components/common/Estado.jsx';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import EscanerCodigo from '../../components/common/EscanerCodigo.jsx';
import { FilaEstado } from '../../components/common/Cargando.jsx';
import { estadoEjemplar } from '../../utils/estados.js';
import { codigo, fmtSoles } from '../../utils/formato.js';

function Ejemplares() {
  const [params, setParams] = useSearchParams();
  const [busqueda, setBusqueda] = useState('');
  const [estado, setEstado] = useState('');
  const [nuevo, setNuevo] = useState(false);
  const [detalle, setDetalle] = useState(null);
  const [libroInicial, setLibroInicial] = useState(null);
  const q = useDebounce(busqueda);
  const { datos: ejemplares, cargando, recargar } = useApi(() => getEjemplares({ q, estado: estado || undefined }), [q, estado], []);

  // Desde la ficha de un libro se llega aquí con ?libro=ID: se abre el alta de una copia de ese libro.
  const libroParam = params.get('libro');
  useEffect(() => {
    if (libroParam) {
      setLibroInicial(libroParam);
      setNuevo(true);
      params.delete('libro');
      setParams(params, { replace: true });
    }
  }, [libroParam]);

  return (
    <div>
      <div className="page-head">
        <div><h1>Ejemplares</h1><p>Copias físicas de cada libro (RF14).</p></div>
        <button className="btn-primary" onClick={() => setNuevo(true)}>+ Registrar ejemplar</button>
      </div>

      <div className="toolbar">
        <SearchInput placeholder="Buscar por código, ubicación o título..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        <select className="select-inline" value={estado} onChange={(e) => setEstado(e.target.value)}>
          <option value="">Todos los estados</option>
          {Object.entries(estadoEjemplar).map(([v, [, t]]) => <option key={v} value={v}>{t}</option>)}
        </select>
      </div>

      <div className="card table-card">
        <table>
          <thead><tr><th>Identificación</th><th>Código</th><th>Libro</th><th>Estado</th><th>Ubicación</th><th>Precio</th><th>Acciones</th></tr></thead>
          <tbody>
            {(cargando || ejemplares.length === 0) && <FilaEstado columnas={7} cargando={cargando} vacio="No hay ejemplares." />}
            {!cargando && ejemplares.map((e) => (
              <tr key={e.id}>
                <td className="mono">{codigo('E', e.id)}</td>
                <td className="mono">{e.codigo_inventario}</td>
                <td>{e.titulo_libro}</td>
                <td><Estado valor={e.estado} mapa={estadoEjemplar} /></td>
                <td>{e.ubicacion || '—'}</td>
                <td className="mono">{(e.precio ?? e.precio_libro) != null ? fmtSoles(e.precio ?? e.precio_libro) : '—'}</td>
                <td><button className="btn-link" onClick={() => setDetalle(e)}>👁 Ver detalle</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {nuevo && (
        <NuevoEjemplar
          libroInicial={libroInicial}
          onClose={() => { setNuevo(false); setLibroInicial(null); }}
          onHecho={() => { setNuevo(false); setLibroInicial(null); recargar(); }}
        />
      )}
      {detalle && <DetalleEjemplar ejemplar={detalle} onClose={() => setDetalle(null)} onHecho={() => { setDetalle(null); recargar(); }} />}
    </div>
  );
}

function NuevoEjemplar({ libroInicial, onClose, onHecho }) {
  const [f, setF] = useState({ id_libro: libroInicial || '', codigo_inventario: '', ubicacion: '', precio: '' });
  const { datos: libros } = useApi(() => getLibros(), [], []);
  const { ejecutar, enviando } = useAccion();
  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });

  const guardar = async (e) => {
    e.preventDefault();
    const r = await ejecutar(
      () => crearEjemplar(f.id_libro, {
        codigo_inventario: f.codigo_inventario,
        ubicacion: f.ubicacion,
        precio: f.precio === '' ? null : Number(f.precio),
      }),
      'Ejemplar registrado'
    );
    if (r) onHecho();
  };

  return (
    <Modal titulo="Registrar ejemplar" subtitulo="Si hay lectores esperando este libro, se le apartará al primero de la cola."
           onClose={onClose}
           pie={<><button className="btn-ghost" onClick={onClose}>Cancelar</button><button className="btn-primary" form="form-ej" disabled={enviando}>Guardar</button></>}>
      <form id="form-ej" className="form-grid" onSubmit={guardar}>
        <Campo label="Libro" ancho={2}>
          <select value={f.id_libro} onChange={set('id_libro')} required>
            <option value="">Seleccione un libro...</option>
            {libros.filter((l) => l.tipo_libro !== 'VIRTUAL').map((l) => (
              <option key={l.id} value={l.id}>{l.titulo} ({l.ejemplares_total} ejemplares)</option>
            ))}
          </select>
        </Campo>
        <Campo label="Código de inventario" ayuda="Escanea el código de la etiqueta o escríbelo.">
          <div style={{ display: 'flex', gap: 6 }}>
            <input value={f.codigo_inventario} onChange={set('codigo_inventario')} placeholder="INV-001" required />
            <EscanerCodigo
              texto="📷"
              etiqueta="📷"
              onCodigo={(codigo) => setF({ ...f, codigo_inventario: (codigo || '').replace(/[\s-]/g, '').toUpperCase() })}
            />
          </div>
        </Campo>
        <Campo label="Ubicación"><input value={f.ubicacion} onChange={set('ubicacion')} placeholder="Estante A1" /></Campo>
        <Campo label="Precio de esta copia (S/)" ayuda="Si lo dejas vacío, toma el precio general del libro.">
          <input type="number" step="0.01" min="0" value={f.precio} onChange={set('precio')} placeholder="0.00" />
        </Campo>
      </form>
    </Modal>
  );
}

function DetalleEjemplar({ ejemplar, onClose, onHecho }) {
  const [f, setF] = useState({
    ubicacion: ejemplar.ubicacion || '',
    estado: ejemplar.estado,
    precio: ejemplar.precio ?? '',
  });
  const { ejecutar, enviando } = useAccion();
  const bloqueado = ['PRESTADO', 'RESERVADO'].includes(ejemplar.estado);

  const guardar = async (e) => {
    e.preventDefault();
    const datos = {
      ubicacion: f.ubicacion,
      estado: f.estado !== ejemplar.estado ? f.estado : null,
      // Si el campo queda vacío, la copia vuelve al precio general del libro
      // (el backend no puede adivinarlo: null significa "no cambiar").
      precio: f.precio === '' ? (ejemplar.precio_libro ?? null) : Number(f.precio),
    };
    const r = await ejecutar(() => actualizarEjemplar(ejemplar.id, datos), 'Ejemplar actualizado');
    if (r) onHecho();
  };

  return (
    <Modal titulo={`Ejemplar ${ejemplar.codigo_inventario}`} subtitulo={ejemplar.titulo_libro} onClose={onClose}
           pie={<><button className="btn-ghost" onClick={onClose}>Cerrar</button><button className="btn-primary" form="form-ej2" disabled={enviando}>Guardar cambios</button></>}>
      <dl className="detalle" style={{ marginBottom: 18 }}>
        <dt>Identificación</dt><dd className="mono">{codigo('E', ejemplar.id)}</dd>
        <dt>Estado actual</dt><dd><Estado valor={ejemplar.estado} mapa={estadoEjemplar} /></dd>
        <dt>Precio del libro</dt><dd>{ejemplar.precio_libro != null ? fmtSoles(ejemplar.precio_libro) : '—'}</dd>
      </dl>
      <form id="form-ej2" className="form-grid" onSubmit={guardar}>
        <Campo label="Ubicación"><input value={f.ubicacion} onChange={(e) => setF({ ...f, ubicacion: e.target.value })} /></Campo>
        <Campo label="Precio de esta copia (S/)" ayuda="Este precio es el que se copia al préstamo. Si lo vacías, vuelve al precio del libro.">
          <input type="number" step="0.01" min="0" value={f.precio} onChange={(e) => setF({ ...f, precio: e.target.value })} />
        </Campo>
        <Campo label="Estado" ayuda={bloqueado ? 'Cambia solo con la devolución o la reserva.' : 'Para dar de baja elija "De baja".'}>
          <select value={f.estado} onChange={(e) => setF({ ...f, estado: e.target.value })} disabled={bloqueado}>
            {bloqueado && <option value={ejemplar.estado}>{estadoEjemplar[ejemplar.estado][1]}</option>}
            <option value="DISPONIBLE">Disponible</option>
            <option value="DANADO">Dañado</option>
            <option value="PERDIDO">Perdido</option>
            <option value="BAJA">De baja</option>
          </select>
        </Campo>
      </form>
    </Modal>
  );
}

export default Ejemplares;
