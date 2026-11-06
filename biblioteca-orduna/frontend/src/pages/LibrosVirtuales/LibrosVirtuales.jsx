// Panel del mostrador: reservas de libro virtual.
// El personal busca la orden con el codigo que muestra el cliente (vale 24 horas)
// y decide: cancelar la orden, cambiarla o cobrarla.
import { useState } from 'react';
import {
  accesosDeLibro,
  buscarPedidoPorCodigo,
  cancelarPedido,
  cambiarPedido,
  cobrarPedido,
  listarPedidos,
} from '../../api/accesosVirtualesApi.js';
import {
  aprobarComprobante,
  denegarComprobante,
  listarComprobantesPendientes,
  urlImagenComprobante,
} from '../../api/comprobantesApi.js';
import { buscarLibros } from '../../api/librosApi.js';
import { useApi, useAccion, useDebounce } from '../../hooks.js';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import Estado from '../../components/common/Estado.jsx';
import { Cargando, Vacio, FilaEstado } from '../../components/common/Cargando.jsx';
import {
  estadoAccesoVirtual,
  estadoComprobante,
  metodoPagoVirtual,
  tipoAccesoVirtual,
} from '../../utils/estados.js';
import { fmtFechaHora, fmtSoles } from '../../utils/formato.js';
import './LibrosVirtuales.css';

function LibrosVirtuales() {
  const [codigo, setCodigo] = useState('');
  const [orden, setOrden] = useState(null);
  const [cambios, setCambios] = useState(false);
  const { ejecutar, enviando } = useAccion();
  const { datos: pendientes, cargando, recargar } = useApi(() => listarPedidos('PENDIENTE_PAGO'), [], []);

  /** El cliente muestra su codigo en el mostrador: con el se encuentra la orden. */
  const buscar = async () => {
    const r = await ejecutar(() => buscarPedidoPorCodigo(codigo.trim()), null);
    if (r && typeof r === 'object') setOrden(r);
  };

  const cobrar = async () => {
    const r = await ejecutar(() => cobrarPedido(orden.id), 'Orden cobrada: el libro ya está del cliente');
    if (r) { setOrden(r); recargar(); }
  };

  const cancelar = async () => {
    const r = await ejecutar(() => cancelarPedido(orden.id), 'Orden cancelada');
    if (r) { setOrden(r); recargar(); }
  };

  const cobrarDeLaTabla = async (id) => {
    const r = await ejecutar(() => cobrarPedido(id), 'Orden cobrada: el libro ya está del cliente');
    if (r) recargar();
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Reservas de libro virtual</h1>
          <p>Pedidos de libros virtuales pendientes de cobro</p>
        </div>
      </div>

      <div className="lv-buscador card">
        <div>
          <h3 className="seccion-titulo">Código del cliente</h3>
          <p className="pick-sub">
            El cliente elige pagar en la tienda y te muestra un código de 8 letras que vale 24 horas.
          </p>
        </div>
        <div style={{ display: 'flex', gap: 8 }}>
          <input
            className="input"
            value={codigo}
            onChange={(e) => setCodigo(e.target.value.toUpperCase())}
            placeholder="ABCD2345"
            onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); buscar(); } }}
          />
          <button className="btn-primary" onClick={buscar} disabled={enviando || !codigo.trim()}>
            {enviando ? 'Buscando...' : 'Buscar'}
          </button>
        </div>
      </div>

      {orden && (
        <OrdenEncontrada
          orden={orden}
          enviando={enviando}
          onCobrar={cobrar}
          onCancelar={cancelar}
          onCambiar={() => setCambios(true)}
        />
      )}

      <h3 className="seccion-titulo" style={{ marginTop: 26 }}>Pedidos pendientes</h3>
      <div className="card table-card">
        <table>
          <thead>
            <tr><th>Código</th><th>Cliente</th><th>Libro</th><th>Tipo</th><th>Pago</th><th>Monto</th><th>Fecha</th><th></th></tr>
          </thead>
          <tbody>
            {(cargando || pendientes.length === 0) && (
              <FilaEstado columnas={8} cargando={cargando} vacio="No hay pedidos pendientes de cobro." />
            )}
            {!cargando && pendientes.map((p) => (
              <tr key={p.id}>
                <td className="mono">{p.codigo_retiro || '—'}</td>
                <td>{p.nombre_usuario || '—'}</td>
                <td>{p.titulo}</td>
                <td><Estado valor={p.tipo} mapa={tipoAccesoVirtual} /></td>
                <td>
                  {metodoPagoVirtual[p.metodo_pago] || p.metodo_pago || '—'}
                  {p.metodo_pago === 'YAPE' && p.estado_comprobante && (
                    <div style={{ marginTop: 4 }}>
                      <Estado valor={p.estado_comprobante} mapa={estadoComprobante} />
                    </div>
                  )}
                </td>
                <td className="mono">{fmtSoles(p.precio)}</td>
                <td className="mono">{fmtFechaHora(p.fecha_creacion)}</td>
                <td>
                  <div className="acciones">
                    <button className="btn-primary btn-sm" onClick={() => cobrarDeLaTabla(p.id)} disabled={enviando}>
                      {p.metodo_pago === 'YAPE' ? 'Cobrar en mostrador' : 'Cobrar'}
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <ComprobantesYape />

      <ComprasPorLibro />

      {cambios && orden && (
        <Modal
          titulo="Cambiar la orden"
          subtitulo={orden.titulo}
          onClose={() => setCambios(false)}
          ancho={480}
          pie={
            <>
              <button className="btn-ghost" onClick={() => setCambios(false)}>Cancelar</button>
              <button className="btn-primary" form="form-cambiar" disabled={enviando}>Guardar cambio</button>
            </>
          }
        >
          <FormCambiar
            orden={orden}
            onGuardar={async (datos) => {
              const r = await ejecutar(() => cambiarPedido(orden.id, datos), 'Orden actualizada');
              if (r) { setOrden(r); setCambios(false); recargar(); }
            }}
          />
        </Modal>
      )}
    </div>
  );
}

/** La orden que se encontró con el código del cliente, con las tres decisiones del mostrador. */
function OrdenEncontrada({ orden, onCobrar, onCancelar, onCambiar, enviando }) {
  const pendiente = orden.estado === 'PENDIENTE_PAGO';
  return (
    <div className="card" style={{ padding: 18, marginTop: 16 }}>
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 14, flexWrap: 'wrap' }}>
        <div style={{ flex: 1, minWidth: 260 }}>
          <div className="pick-title">{orden.titulo}</div>
          <div className="pick-sub">{orden.nombre_usuario} · {orden.email_usuario}</div>
        </div>
        <Estado valor={orden.estado} mapa={estadoAccesoVirtual} />
      </div>

      <dl className="detalle" style={{ marginTop: 14 }}>
        <dt>Código</dt><dd className="mono">{orden.codigo_retiro || '—'}</dd>
        <dt>Número de cola</dt><dd className="mono">{orden.numero_cola || '—'}</dd>
        <dt>Tipo</dt><dd><Estado valor={orden.tipo} mapa={tipoAccesoVirtual} /></dd>
        <dt>Días</dt><dd>{orden.dias_alquiler || '—'}</dd>
        <dt>Pago</dt><dd>{metodoPagoVirtual[orden.metodo_pago] || orden.metodo_pago || '—'}</dd>
        <dt>Monto</dt><dd>{fmtSoles(orden.precio)}</dd>
        <dt>El código vence</dt><dd>{fmtFechaHora(orden.fecha_codigo_expira)}</dd>
      </dl>

      {pendiente ? (
        <div className="acciones" style={{ marginTop: 14 }}>
          <button className="btn-danger" onClick={onCancelar} disabled={enviando}>Cancelar orden</button>
          <button className="btn-ghost" onClick={onCambiar} disabled={enviando}>Cambiar orden</button>
          <button className="btn-primary" onClick={onCobrar} disabled={enviando}>Cobrar orden</button>
        </div>
      ) : (
        <div className="info-box" style={{ marginTop: 14 }}>
          Esta orden ya no está pendiente: el libro virtual se activó con ella.
        </div>
      )}
    </div>
  );
}

/** Cambia el tipo de la orden (alquiler o compra) y los días del alquiler. */
function FormCambiar({ orden, onGuardar }) {
  const [tipo, setTipo] = useState(orden.tipo);
  const [dias, setDias] = useState(orden.dias_alquiler || 7);

  return (
    <form
      id="form-cambiar"
      className="form-grid"
      onSubmit={(e) => {
        e.preventDefault();
        onGuardar({ tipo, dias_alquiler: tipo === 'ALQUILER' ? Number(dias) : null });
      }}
    >
      <Campo label="Tipo" ancho={2}>
        <div className="chips">
          <span className={'chip' + (tipo === 'ALQUILER' ? ' on' : '')} onClick={() => setTipo('ALQUILER')}>Alquiler</span>
          <span className={'chip' + (tipo === 'COMPRA' ? ' on' : '')} onClick={() => setTipo('COMPRA')}>Compra</span>
        </div>
      </Campo>
      {tipo === 'ALQUILER' && (
        <Campo label="Días de alquiler" ancho={2}>
          <input type="number" min="1" max="365" value={dias} onChange={(e) => setDias(e.target.value)} />
        </Campo>
      )}
    </form>
  );
}

/**
 * Comprobantes de Yape que subieron los lectores.
 *
 * El personal mira la captura, la acepta (el lector ya tiene el libro virtual) o la
 * deniega con un motivo (el lector puede subir otra captura).
 */
function ComprobantesYape() {
  const { datos: comprobantes, cargando, recargar } = useApi(() => listarComprobantesPendientes(), [], []);
  const { ejecutar, enviando } = useAccion();
  const [denegando, setDenegando] = useState(null);
  const [motivo, setMotivo] = useState('');

  /**
   * Abre la captura en otra pestaña. El enlace caduca en 15 minutos.
   * La pestaña se abre antes del await: despues, el navegador la bloquearia como emergente.
   */
  const verImagen = async (id) => {
    const pestana = window.open('', '_blank');
    const r = await ejecutar(() => urlImagenComprobante(id), null);
    const url = typeof r === 'string' ? r : r?.url;
    if (!pestana) {
      if (url) window.open(url, '_blank', 'noopener');
      return;
    }
    if (url) {
      pestana.opener = null;
      pestana.location.href = url;
    } else {
      pestana.close();
    }
  };

  const aprobar = async (id) => {
    const r = await ejecutar(() => aprobarComprobante(id), 'Comprobante aceptado: el lector ya tiene el libro');
    if (r) recargar();
  };

  const confirmarDenegar = async () => {
    if (!motivo.trim()) return;
    const r = await ejecutar(() => denegarComprobante(denegando.id, motivo.trim()), 'Comprobante denegado');
    if (r) { setDenegando(null); setMotivo(''); recargar(); }
  };

  return (
    <div style={{ marginTop: 26 }}>
      <h3 className="seccion-titulo">Comprobantes de Yape por revisar</h3>
      <div className="card table-card">
        <table>
          <thead>
            <tr>
              <th>Cliente</th><th>Libro</th><th>Tipo</th><th>Monto</th>
              <th>Código de operación</th><th>Enviado</th><th>Estado</th><th></th>
            </tr>
          </thead>
          <tbody>
            {(cargando || (comprobantes || []).length === 0) && (
              <FilaEstado columnas={8} cargando={cargando} vacio="No hay comprobantes por revisar." />
            )}
            {!cargando && (comprobantes || []).map((c) => (
              <tr key={c.id}>
                <td>{c.nombre_usuario}</td>
                <td>{c.titulo}</td>
                <td><Estado valor={c.tipo} mapa={tipoAccesoVirtual} /></td>
                <td className="mono">{fmtSoles(c.monto)}</td>
                <td className="mono">{c.referencia_yape || '—'}</td>
                <td className="mono">{fmtFechaHora(c.fecha_envio)}</td>
                <td><Estado valor={c.estado} mapa={estadoComprobante} /></td>
                <td>
                  <div className="acciones">
                    <button className="btn-ghost btn-sm" onClick={() => verImagen(c.id)} disabled={enviando}>
                      Ver comprobante
                    </button>
                    <button className="btn-primary btn-sm" onClick={() => aprobar(c.id)} disabled={enviando}>
                      Aceptar
                    </button>
                    <button className="btn-danger btn-sm" onClick={() => setDenegando(c)} disabled={enviando}>
                      Denegar
                    </button>
                  </div>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {denegando && (
        <Modal
          titulo="Denegar el comprobante"
          subtitulo={denegando.titulo}
          onClose={() => { setDenegando(null); setMotivo(''); }}
          ancho={480}
          pie={
            <>
              <button className="btn-ghost" onClick={() => { setDenegando(null); setMotivo(''); }}>Cancelar</button>
              <button className="btn-danger" onClick={confirmarDenegar} disabled={enviando || !motivo.trim()}>
                {enviando ? 'Denegando...' : 'Denegar comprobante'}
              </button>
            </>
          }
        >
          <div className="form-grid">
            <Campo
              label="Motivo"
              ancho={2}
              ayuda="El lector lo ve en su aviso: sé claro (por ejemplo, que la captura no se ve o que el monto no coincide)."
            >
              <textarea
                value={motivo}
                onChange={(e) => setMotivo(e.target.value)}
                maxLength={300}
                placeholder="La captura se ve borrosa, vuelve a tomarla"
              />
            </Campo>
          </div>
        </Modal>
      )}
    </div>
  );
}

/** Quién compró o alquiló cada libro virtual (lo ve el personal). */
function ComprasPorLibro() {
  const [texto, setTexto] = useState('');
  const q = useDebounce(texto, 350);
  const [idLibro, setIdLibro] = useState(null);
  const [titulo, setTitulo] = useState('');
  const { datos: libros } = useApi(
    () => (q.trim() ? buscarLibros(q.trim()) : Promise.resolve({ status: 200, data: [] })),
    [q],
    []
  );
  const { datos: lista, cargando } = useApi(
    () => (idLibro ? accesosDeLibro(idLibro) : Promise.resolve({ status: 200, data: [] })),
    [idLibro],
    []
  );

  return (
    <div style={{ marginTop: 26 }}>
      <h3 className="seccion-titulo">Compras de un libro virtual</h3>
      <div className="card" style={{ padding: 16 }}>
        <div style={{ display: 'flex', gap: 8, marginBottom: 12 }}>
          <input
            className="input"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            placeholder="Buscar el libro por título o ISBN..."
          />
        </div>
        {texto.trim() && libros.length > 0 && (
          <div className="chips" style={{ marginBottom: 12 }}>
            {libros.filter((l) => l.tipo_libro !== 'FISICO').map((l) => (
              <span key={l.id} className={'chip' + (idLibro === l.id ? ' on' : '')} onClick={() => { setIdLibro(l.id); setTitulo(l.titulo); }}>
                {l.titulo}
              </span>
            ))}
          </div>
        )}
        {idLibro ? (
          cargando ? <Cargando /> : lista.length === 0 ? (
            <Vacio texto="Nadie ha comprado ni alquilado este libro todavía." />
          ) : (
            <div className="table-card" style={{ border: '1px solid var(--border)', borderRadius: 8 }}>
              <table>
                <thead>
                  <tr><th>Cliente</th><th>Correo</th><th>Tipo</th><th>Estado</th><th>Monto</th><th>Desde</th><th>Hasta</th></tr>
                </thead>
                <tbody>
                  {lista.map((a) => (
                    <tr key={a.id}>
                      <td>{a.nombre_usuario}</td>
                      <td>{a.email_usuario}</td>
                      <td><Estado valor={a.tipo} mapa={tipoAccesoVirtual} /></td>
                      <td><Estado valor={a.estado} mapa={estadoAccesoVirtual} /></td>
                      <td className="mono">{fmtSoles(a.precio)}</td>
                      <td className="mono">{a.fecha_inicio || '—'}</td>
                      <td className="mono">{a.fecha_fin || '—'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )
        ) : (
          <div className="info-box">Busca un libro virtual para ver quién lo compró o lo alquiló.</div>
        )}
      </div>
    </div>
  );
}

export default LibrosVirtuales;
