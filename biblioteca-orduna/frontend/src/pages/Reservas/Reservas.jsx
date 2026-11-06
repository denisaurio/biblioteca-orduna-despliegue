// Réplica del prototipo Figma "RESERVAS" (RF09): tabla de reservas + panel lateral "Cola de reservas"
// (FIFO por libro). Al devolverse un ejemplar, el backend lo aparta para el primero de la cola.
import { useEffect, useState } from 'react';
import { getReservas, getColaLibro, cancelarReserva, crearReservaMostrador } from '../../api/reservasApi.js';
import { getUsuarios } from '../../api/usuariosApi.js';
import { getLibros, buscarLibros } from '../../api/librosApi.js';
import { useApi, useAccion, useDebounce } from '../../hooks.js';
import Modal from '../../components/common/Modal.jsx';
import SearchInput from '../../components/common/SearchInput.jsx';
import Estado from '../../components/common/Estado.jsx';
import { FilaEstado, Cargando, Vacio } from '../../components/common/Cargando.jsx';
import { estadoLibro, estadoReserva, estadoUsuario } from '../../utils/estados.js';
import { codigo, fmtFecha, iniciales } from '../../utils/formato.js';
import '../Prestamos/Prestamos.css';

const ACTIVAS = ['PENDIENTE', 'NOTIFICADA'];

function Reservas() {
  const [busqueda, setBusqueda] = useState('');
  const [vista, setVista] = useState('activas');
  const [libroCola, setLibroCola] = useState(null);
  const [nueva, setNueva] = useState(false);
  const { datos: reservas, cargando, recargar } = useApi(() => getReservas(), [], []);
  const { ejecutar } = useAccion();

  const filtradas = reservas
    .filter((r) => vista === 'todas' || (vista === 'activas' ? ACTIVAS.includes(r.estado) : !ACTIVAS.includes(r.estado)))
    .filter((r) => `${r.titulo_libro} ${r.nombre_lector}`.toLowerCase().includes(busqueda.toLowerCase()));

  // Panel lateral: por defecto la cola del primer libro con reservas activas.
  useEffect(() => {
    if (!libroCola) {
      const primera = reservas.find((r) => ACTIVAS.includes(r.estado));
      if (primera) setLibroCola(primera.id_libro);
    }
  }, [reservas, libroCola]);

  const cancelar = async (r) => {
    if (!window.confirm(`¿Cancelar la reserva de ${r.nombre_lector}?`)) return;
    const ok = await ejecutar(() => cancelarReserva(r.id), 'Reserva cancelada');
    if (ok) recargar();
  };

  return (
    <div>
      <div className="page-head">
        <div><h1>Reservas</h1><p>Reservas en línea (RF09) — se atienden por orden de llegada (FIFO).</p></div>
        <button className="btn-primary" onClick={() => setNueva(true)}>+ Nueva reserva</button>
      </div>

      {nueva && (
        <NuevaReservaMostrador
          onClose={() => setNueva(false)}
          onHecho={(r) => { setNueva(false); setLibroCola(r.id_libro); recargar(); }}
        />
      )}

      <div className="wizard-grid" style={{ gridTemplateColumns: '1.7fr 1fr' }}>
        <div>
          <div className="toolbar">
            <SearchInput placeholder="Buscar reserva por libro o usuario..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
            <div className="tabs">
              {[['activas', 'Activas'], ['cerradas', 'Cerradas'], ['todas', 'Todas']].map(([v, t]) => (
                <button key={v} className={vista === v ? 'active' : ''} onClick={() => setVista(v)}>{t}</button>
              ))}
            </div>
          </div>
          <div className="card table-card">
            <table>
              <thead><tr><th>Identificación</th><th>Libro</th><th>Usuario</th><th>Fecha</th><th>Estado</th><th></th></tr></thead>
              <tbody>
                {(cargando || filtradas.length === 0) && <FilaEstado columnas={6} cargando={cargando} vacio="No hay reservas en esta vista." />}
                {!cargando && filtradas.map((r) => (
                  <tr key={r.id} onClick={() => setLibroCola(r.id_libro)} style={{ cursor: 'pointer' }}
                      className={libroCola === r.id_libro ? 'fila-sel' : ''}>
                    <td className="mono">{codigo('R', r.id)}</td>
                    <td>{r.titulo_libro}</td>
                    <td>{r.nombre_lector}</td>
                    <td className="mono">{fmtFecha(r.fecha_reserva)}</td>
                    <td>
                      <Estado valor={r.estado} mapa={estadoReserva} />
                      {r.estado === 'NOTIFICADA' && <div className="pick-sub">Hasta {fmtFecha(r.fecha_limite_retiro)} · {r.codigo_ejemplar_asignado}</div>}
                    </td>
                    <td onClick={(e) => e.stopPropagation()}>
                      {ACTIVAS.includes(r.estado) && <button className="btn-link" onClick={() => cancelar(r)}>Cancelar</button>}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>

        <ColaReservas idLibro={libroCola} version={reservas} />
      </div>
    </div>
  );
}

/**
 * El personal anota a un lector en la cola: elige el lector y un libro SIN copias
 * disponibles (si hay una disponible, se presta en "Préstamos", no se reserva).
 */
function NuevaReservaMostrador({ onClose, onHecho }) {
  const [buscaU, setBuscaU] = useState('');
  const [buscaL, setBuscaL] = useState('');
  const [lector, setLector] = useState(null);
  const [libro, setLibro] = useState(null);
  const qU = useDebounce(buscaU);
  const qL = useDebounce(buscaL);
  const { ejecutar, enviando } = useAccion();
  const { datos: usuarios, cargando: cargandoU } = useApi(
    () => getUsuarios({ q: qU, rol: 'LECTOR', tamanio: 6 }), [qU], { contenido: [] });
  const { datos: libros, cargando: cargandoL } = useApi(
    () => (qL.trim() ? buscarLibros(qL.trim()) : getLibros()), [qL], []);

  /** Por qué un libro no se puede reservar (o null si sí se puede). */
  const motivo = (l) => {
    if (l.tipo_libro === 'VIRTUAL') return 'Virtual: se alquila o se compra';
    if (l.ejemplares_disponibles > 0) return 'Hay copia disponible: préstalo';
    return null;
  };

  const confirmar = async () => {
    const r = await ejecutar(() => crearReservaMostrador(libro.id, lector.id), `Reserva registrada para ${lector.nombre}`);
    if (r) onHecho(r);
  };

  return (
    <Modal
      titulo="Nueva reserva"
      subtitulo="Anota a un lector en la cola de un libro sin copias disponibles"
      onClose={onClose}
      ancho={720}
      pie={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancelar</button>
          <button className="btn-primary" disabled={!lector || !libro || enviando} onClick={confirmar}>
            {enviando ? 'Reservando...' : 'Reservar'}
          </button>
        </>
      }
    >
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 18 }}>
        <div>
          <label>1. Lector</label>
          <input className="wizard-input" placeholder="Nombre, DNI o correo" value={buscaU}
                 onChange={(e) => setBuscaU(e.target.value)} autoFocus />
          <div className="pick-list">
            {cargandoU ? <Cargando /> : usuarios.contenido.length === 0 ? <Vacio texto="No se encontraron lectores." /> :
              usuarios.contenido.map((u) => {
                const bloqueado = u.estado !== 'ACTIVO';
                return (
                  <div key={u.id}
                       className={'pick-item' + (lector?.id === u.id ? ' selected' : '') + (bloqueado ? ' bloqueado' : '')}
                       onClick={() => !bloqueado && setLector(u)}>
                    <span className="pick-avatar">{iniciales(`${u.nombre} ${u.apellido}`)}</span>
                    <div>
                      <div className="pick-title">{u.nombre} {u.apellido}</div>
                      <div className="pick-sub">Código {codigo('U', u.id)}</div>
                    </div>
                    {bloqueado ? <span style={{ marginLeft: 'auto' }}><Estado valor={u.estado} mapa={estadoUsuario} /></span>
                      : lector?.id === u.id && <span className="check">✓</span>}
                  </div>
                );
              })}
          </div>
        </div>
        <div>
          <label>2. Libro</label>
          <input className="wizard-input" placeholder="Título, autor o ISBN" value={buscaL}
                 onChange={(e) => setBuscaL(e.target.value)} />
          <div className="pick-list">
            {cargandoL ? <Cargando /> : libros.length === 0 ? <Vacio texto="No se encontraron libros." /> :
              libros.map((l) => {
                const no = motivo(l);
                return (
                  <div key={l.id}
                       className={'pick-item' + (libro?.id === l.id ? ' selected' : '') + (no ? ' bloqueado' : '')}
                       onClick={() => !no && setLibro(l)}
                       title={no || 'Reservable'}>
                    <span className="pick-avatar book">📖</span>
                    <div>
                      <div className="pick-title">{l.titulo}</div>
                      <div className="pick-sub">{no || `${l.ejemplares_total} copia(s), ninguna disponible`}</div>
                    </div>
                    {libro?.id === l.id ? <span className="check">✓</span>
                      : <span style={{ marginLeft: 'auto' }}><Estado valor={l.estado} mapa={estadoLibro} /></span>}
                  </div>
                );
              })}
          </div>
        </div>
      </div>
      <p className="hint" style={{ marginTop: 12 }}>
        Cuando se devuelva o se registre una copia, el sistema se la aparta al primero de la cola y le avisa.
      </p>
    </Modal>
  );
}

function ColaReservas({ idLibro, version }) {
  const { datos: cola, cargando } = useApi(() => (idLibro ? getColaLibro(idLibro) : Promise.resolve({ data: null })), [idLibro, version]);

  return (
    <div className="card" style={{ padding: 18 }}>
      <h3 className="seccion-titulo">Cola de reservas</h3>
      {!idLibro ? <Vacio texto="Selecciona una reserva para ver la cola del libro." /> : cargando ? <Cargando /> : cola && (
        <>
          <p className="pick-sub" style={{ marginTop: -6, marginBottom: 12 }}>{cola.titulo_libro}</p>
          {cola.cola.length === 0 ? <Vacio texto="Nadie en espera para este libro." /> : cola.cola.map((r, i) => (
            <div key={r.id} className="cola-item">
              <span className="cola-num">{i + 1}</span>
              <div style={{ flex: 1 }}>
                <div className="pick-title">{r.nombre_lector}</div>
                <div className="pick-sub">
                  {r.estado === 'NOTIFICADA' ? `Por recoger hasta ${fmtFecha(r.fecha_limite_retiro)}` : `En espera desde ${fmtFecha(r.fecha_reserva)}`}
                </div>
              </div>
              <Estado valor={r.estado} mapa={estadoReserva} />
            </div>
          ))}
        </>
      )}
    </div>
  );
}

export default Reservas;
