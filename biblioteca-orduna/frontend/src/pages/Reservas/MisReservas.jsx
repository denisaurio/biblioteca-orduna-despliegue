// Portal del lector: "Mis reservas" (GET /api/mi/reservas) y reservar un libro sin ejemplares
// disponibles (POST /api/reservas). Si el libro tiene ejemplares libres, se pide en mostrador.
import { useState } from 'react';
import { getMisReservas } from '../../api/miCuentaApi.js';
import { crearReserva, cancelarReserva } from '../../api/reservasApi.js';
import { buscarLibros, getLibros } from '../../api/librosApi.js';
import { useApi, useAccion, useDebounce } from '../../hooks.js';
import Estado from '../../components/common/Estado.jsx';
import Modal from '../../components/common/Modal.jsx';
import { FilaEstado, Cargando, Vacio } from '../../components/common/Cargando.jsx';
import { estadoLibro, estadoReserva } from '../../utils/estados.js';
import { fmtFecha } from '../../utils/formato.js';

function MisReservas() {
  const [nueva, setNueva] = useState(false);
  const { datos: reservas, cargando, recargar } = useApi(() => getMisReservas(), [], []);
  const { ejecutar } = useAccion();

  const cancelar = async (r) => {
    if (!window.confirm(`¿Cancelar tu reserva de "${r.titulo_libro}"?`)) return;
    const ok = await ejecutar(() => cancelarReserva(r.id), 'Reserva cancelada');
    if (ok) recargar();
  };

  const listas = reservas.filter((r) => r.estado === 'NOTIFICADA');

  return (
    <div>
      <div className="page-head">
        <div><h1>Mis reservas</h1><p>Reserva libros que están prestados y te avisaremos cuando estén disponibles.</p></div>
        <button className="btn-primary" onClick={() => setNueva(true)}>+ Nueva reserva</button>
      </div>

      {listas.map((r) => (
        <div key={r.id} className="ok-form" style={{ fontSize: 13.5 }}>
          📘 <b>{r.titulo_libro}</b> ya está disponible para ti (ejemplar {r.codigo_ejemplar_asignado}). Recógelo hasta el <b>{fmtFecha(r.fecha_limite_retiro)}</b>.
        </div>
      ))}

      <div className="card table-card">
        <table>
          <thead><tr><th>Libro</th><th>Fecha de reserva</th><th>Posición en la cola</th><th>Estado</th><th></th></tr></thead>
          <tbody>
            {(cargando || reservas.length === 0) && <FilaEstado columnas={5} cargando={cargando} vacio="No tienes reservas." />}
            {!cargando && reservas.map((r) => (
              <tr key={r.id}>
                <td>{r.titulo_libro}</td>
                <td className="mono">{fmtFecha(r.fecha_reserva)}</td>
                <td className="mono">{r.estado === 'PENDIENTE' ? `#${r.posicion_cola}` : '—'}</td>
                <td><Estado valor={r.estado} mapa={estadoReserva} /></td>
                <td>{['PENDIENTE', 'NOTIFICADA'].includes(r.estado) && <button className="btn-link" onClick={() => cancelar(r)}>Cancelar</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {nueva && <NuevaReserva onClose={() => setNueva(false)} onHecho={() => { setNueva(false); recargar(); }} />}
    </div>
  );
}

function NuevaReserva({ onClose, onHecho }) {
  const [busqueda, setBusqueda] = useState('');
  const q = useDebounce(busqueda);
  const { datos: libros, cargando } = useApi(() => (q.trim() ? buscarLibros(q.trim()) : getLibros()), [q], []);
  const { ejecutar, enviando } = useAccion();

  const reservar = async (libro) => {
    const r = await ejecutar(() => crearReserva(libro.id), `Reservado "${libro.titulo}"`);
    if (r) onHecho();
  };

  return (
    <Modal titulo="Nueva reserva" subtitulo="Solo se reservan libros sin ejemplares disponibles" onClose={onClose} ancho={620}>
      <input className="input" placeholder="Buscar por título, autor o ISBN..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} autoFocus />
      <div style={{ marginTop: 12, maxHeight: 360, overflowY: 'auto' }}>
        {cargando ? <Cargando /> : libros.length === 0 ? <Vacio texto="No se encontraron libros." /> : libros.map((l) => (
          <div key={l.id} className="summary-row">
            <div>
              <div className="pick-title">{l.titulo}</div>
              <div className="pick-sub">{l.autores.map((a) => a.nombre).join(', ')}</div>
            </div>
            {l.tipo_libro === 'VIRTUAL'
              ? <span className="pick-sub">Virtual: alquílalo o cómpralo en Libros</span>
              : l.estado === 'DISPONIBLE'
                ? <span title="Pídelo en mostrador"><Estado valor={l.estado} mapa={estadoLibro} /></span>
                : <button className="btn-primary btn-sm" disabled={enviando} onClick={() => reservar(l)}>Reservar</button>}
          </div>
        ))}
      </div>
    </Modal>
  );
}

export default MisReservas;
