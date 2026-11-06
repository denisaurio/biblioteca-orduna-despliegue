// Réplica del prototipo Figma "LIBROS" (RF01, RF02): vista Catálogo o Lista, buscador
// (título, autor, ISBN o género), "+ Registrar libro" y "Ver detalles".
// El estado se calcula en el backend con los ejemplares disponibles (no se edita a mano).
import { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { useAuth } from '../../context/AuthContext.jsx';
import { getLibros, buscarLibros, getLibroPorId } from '../../api/librosApi.js';
import { crearReserva } from '../../api/reservasApi.js';
import { useApi, useAccion, useDebounce, useRefrescoTiempoReal } from '../../hooks.js';
import SearchInput from '../../components/common/SearchInput.jsx';
import Estado from '../../components/common/Estado.jsx';
import Portada from '../../components/common/Portada.jsx';
import Modal from '../../components/common/Modal.jsx';
import { Cargando, Vacio, FilaEstado } from '../../components/common/Cargando.jsx';
import FormLibro from './FormLibro.jsx';
import PedidoVirtual from './PedidoVirtual.jsx';
import { estadoLibro, tipoLibro } from '../../utils/estados.js';
import { fmtSoles } from '../../utils/formato.js';
import './Libros.css';

function Libros() {
  const { esAdmin, esLector } = useAuth();
  const [params, setParams] = useSearchParams();
  const [vista, setVista] = useState('catalogo');
  const [busqueda, setBusqueda] = useState(params.get('q') || '');
  const q = useDebounce(busqueda);
  const [detalle, setDetalle] = useState(null);
  const [form, setForm] = useState(null); // null | 'nuevo' | libro a editar
  const [pedido, setPedido] = useState(null); // { libro, tipo } para pedir un libro virtual

  const { datos: libros, cargando, recargar } = useApi(() => (q.trim() ? buscarLibros(q.trim()) : getLibros()), [q], []);
  useRefrescoTiempoReal(['libros', 'ejemplares', 'reservas'], recargar);

  // Si llega ?q= desde el buscador de arriba, actualiza la búsqueda.
  useEffect(() => { setBusqueda(params.get('q') || ''); }, [params]);

  // ?ver=ID abre el detalle (desde "Libros destacados" del Inicio).
  useEffect(() => {
    const id = params.get('ver');
    if (id) getLibroPorId(id).then(({ data }) => setDetalle(data)).catch(() => {});
  }, [params]);

  const cerrarDetalle = () => {
    setDetalle(null);
    if (params.get('ver')) { params.delete('ver'); setParams(params, { replace: true }); }
  };

  const autores = (l) => l.autores.map((a) => a.nombre).join(', ');

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Libros</h1>
          <p>Catálogo bibliográfico (RF01, RF02)</p>
        </div>
        {esAdmin && <button className="btn-primary" onClick={() => setForm('nuevo')}>+ Registrar libro</button>}
      </div>

      <div className="toolbar">
        <SearchInput placeholder="Buscar por título, autor, ISBN o género..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        <div className="vista-toggle">
          <button className={vista === 'catalogo' ? 'active' : ''} onClick={() => setVista('catalogo')}>Catálogo</button>
          <button className={vista === 'lista' ? 'active' : ''} onClick={() => setVista('lista')}>Lista</button>
        </div>
      </div>

      {vista === 'catalogo' ? (
        cargando ? <Cargando /> : libros.length === 0 ? <div className="card"><Vacio texto="No se encontraron libros." /></div> : (
          <div className="catalogo-grid">
            {libros.map((libro) => (
              <div className="libro-card" key={libro.id}>
                <Portada libro={libro} />
                <div className="libro-info">
                  <div className="libro-title">{libro.titulo}</div>
                  <div className="libro-author">{autores(libro)}</div>
                  <div className="acciones" style={{ marginBottom: 8 }}>
                    <Estado valor={libro.tipo_libro} mapa={tipoLibro} />
                    <Estado valor={libro.estado} mapa={estadoLibro} />
                  </div>
                  <button className="btn-ghost" style={{ marginTop: 10, width: '100%', justifyContent: 'center' }} onClick={() => setDetalle(libro)}>
                    Ver detalles
                  </button>
                </div>
              </div>
            ))}
          </div>
        )
      ) : (
        <div className="card table-card">
          <table>
            <thead>
              <tr><th>Título</th><th>Autor</th><th>ISBN</th><th>Editorial</th><th>Tipo</th><th>Disponibilidad</th><th>Estado</th><th></th></tr>
            </thead>
            <tbody>
              {(cargando || libros.length === 0) && <FilaEstado columnas={8} cargando={cargando} vacio="No se encontraron libros." />}
              {!cargando && libros.map((libro) => (
                <tr key={libro.id}>
                  <td>{libro.titulo}</td>
                  <td>{autores(libro)}</td>
                  <td className="mono">{libro.isbn}</td>
                  <td>{libro.editorial || '—'}</td>
                  <td><Estado valor={libro.tipo_libro} mapa={tipoLibro} /></td>
                  <td className="mono">
                    {libro.tipo_libro === 'VIRTUAL' ? 'PDF' : `${libro.ejemplares_disponibles} / ${libro.ejemplares_total}`}
                  </td>
                  <td><Estado valor={libro.estado} mapa={estadoLibro} /></td>
                  <td><button className="btn-link" onClick={() => setDetalle(libro)}>Ver</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {detalle && (
        <DetalleLibro
          libro={detalle}
          esAdmin={esAdmin}
          esLector={esLector}
          onClose={cerrarDetalle}
          onEditar={() => { setForm(detalle); setDetalle(null); }}
          onPedir={(tipo) => setPedido({ libro: detalle, tipo })}
        />
      )}

      {pedido && (
        <PedidoVirtual
          libro={pedido.libro}
          tipoInicial={pedido.tipo}
          onClose={() => setPedido(null)}
        />
      )}

      {form && (
        <FormLibro
          libro={form === 'nuevo' ? null : form}
          onClose={() => setForm(null)}
          onGuardado={() => { setForm(null); recargar(); }}
        />
      )}
    </div>
  );
}

function DetalleLibro({ libro, esAdmin, esLector, onClose, onEditar, onPedir }) {
  const { ejecutar, enviando } = useAccion();
  const navegar = useNavigate();
  const [reservado, setReservado] = useState(null);

  const virtual = libro.tipo_libro !== 'FISICO';

  const reservar = async () => {
    const r = await ejecutar(() => crearReserva(libro.id), 'Reserva registrada');
    if (r) setReservado(r);
  };

  return (
    <Modal
      titulo={libro.titulo}
      subtitulo={libro.autores.map((a) => a.nombre).join(', ')}
      onClose={onClose}
      ancho={600}
      pie={
        <>
          {esAdmin && <button className="btn-ghost" onClick={onEditar}>Editar libro</button>}
          {esAdmin && !virtual && (
            <button className="btn-ghost" onClick={() => { onClose(); navegar('/ejemplares?libro=' + libro.id); }}>
              Agregar o editar copias ({libro.ejemplares_total})
            </button>
          )}
          {esLector && virtual && libro.tiene_pdf && (
            <>
              <button className="btn-ghost" onClick={() => onPedir('ALQUILER')}>Alquilar</button>
              <button className="btn-primary" onClick={() => onPedir('COMPRA')}>Comprar</button>
            </>
          )}
          {esLector && !virtual && libro.estado !== 'DISPONIBLE' && !reservado && (
            <button className="btn-primary" onClick={reservar} disabled={enviando}>Reservar este libro</button>
          )}
          <button className="btn-primary" onClick={onClose}>Cerrar</button>
        </>
      }
    >
      <div style={{ display: 'grid', gridTemplateColumns: '140px 1fr', gap: 20 }}>
        <div className="libro-card" style={{ boxShadow: 'none' }}><Portada libro={libro} alto={190} /></div>
        <div>
          <dl className="detalle">
            <dt>ISBN</dt><dd className="mono">{libro.isbn}</dd>
            <dt>Tipo</dt><dd><Estado valor={libro.tipo_libro} mapa={tipoLibro} /></dd>
            <dt>Editorial</dt><dd>{libro.editorial || '—'}</dd>
            <dt>Año</dt><dd>{libro.anio_publicacion || '—'}</dd>
            <dt>Páginas</dt><dd>{libro.num_paginas || '—'}</dd>
            <dt>Géneros</dt><dd>{libro.generos.map((g) => g.nombre).join(', ') || '—'}</dd>
            {virtual ? (
              <>
                <dt>Alquiler</dt><dd>{libro.precio_alquiler_diario != null ? `${fmtSoles(libro.precio_alquiler_diario)} por día` : '—'}</dd>
                <dt>Compra</dt><dd>{libro.precio_compra != null ? fmtSoles(libro.precio_compra) : '—'}</dd>
              </>
            ) : (
              <>
                <dt>Copias (ejemplares)</dt><dd>{libro.ejemplares_disponibles} disponibles de {libro.ejemplares_total}</dd>
                <dt>Precio del ejemplar</dt><dd>{libro.precio_compra != null ? fmtSoles(libro.precio_compra) : '—'}</dd>
              </>
            )}
            <dt>Estado</dt><dd><Estado valor={libro.estado} mapa={estadoLibro} /></dd>
          </dl>
          {esLector && virtual && !libro.tiene_pdf && (
            <div className="info-box" style={{ marginTop: 14 }}>Este libro virtual todavía no tiene el PDF cargado.</div>
          )}
          {esLector && !virtual && libro.estado === 'DISPONIBLE' && (
            <div className="info-box" style={{ marginTop: 14 }}>Hay ejemplares disponibles: solicita el préstamo en el mostrador de la biblioteca.</div>
          )}
          {reservado && (
            <div className="ok-form" style={{ marginTop: 14 }}>
              Reservado. Estás en la posición {reservado.posicion_cola} de la cola; te avisaremos cuando esté disponible.
            </div>
          )}
        </div>
      </div>
    </Modal>
  );
}

export default Libros;
