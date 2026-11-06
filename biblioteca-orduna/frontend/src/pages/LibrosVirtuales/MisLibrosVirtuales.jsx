// Mis libros virtuales (lector): los alquilados, los comprados y los que ya vencieron.
// Los que se vencen salen "en plomo": no se pueden abrir hasta volver a reservarlos.
import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { misLibrosVirtuales, urlPdf } from '../../api/accesosVirtualesApi.js';
import { useApi, useAccion, useRefrescoTiempoReal } from '../../hooks.js';
import Estado from '../../components/common/Estado.jsx';
import Portada from '../../components/common/Portada.jsx';
import { Cargando, Vacio } from '../../components/common/Cargando.jsx';
import { estadoAccesoVirtual, estadoComprobante, tipoAccesoVirtual } from '../../utils/estados.js';
import { fmtFecha, fmtSoles } from '../../utils/formato.js';
import './MisLibrosVirtuales.css';

/** Lista de libros virtuales del usuario. Se usa sola y también dentro del perfil. */
export function ListaMisLibrosVirtuales({ compacta = false }) {
  const navegar = useNavigate();
  const { datos: accesos, cargando, recargar } = useApi(() => misLibrosVirtuales(), [], []);
  useRefrescoTiempoReal(['accesos-virtuales', 'comprobantes'], recargar);
  const { ejecutar, enviando } = useAccion();
  const [mensaje, setMensaje] = useState('');

  /**
   * Pide el enlace firmado del PDF y lo abre. El enlace caduca en 15 minutos.
   * La pestaña se abre ANTES de pedir el enlace: si se abre despues del await, el
   * navegador la toma por una ventana emergente y la bloquea.
   */
  const abrir = async (acceso, descargar = false) => {
    setMensaje('');
    const pestana = descargar ? null : window.open('', '_blank');
    const res = await ejecutar(() => urlPdf(acceso.id, descargar), null);
    const enlace = typeof res === 'string' ? res : res?.url;
    if (!enlace) {
      if (pestana) pestana.close();
      return;
    }
    if (descargar) {
      // El enlace trae "attachment": el navegador guarda el PDF sin salir de la pagina.
      window.location.assign(enlace);
      setMensaje('La descarga del PDF empezó. Si no ves el archivo, revisa la carpeta de descargas.');
    } else if (pestana) {
      pestana.opener = null;
      pestana.location.href = enlace;
    } else {
      window.open(enlace, '_blank', 'noopener');
    }
  };

  if (cargando) return <Cargando />;
  if (!accesos || accesos.length === 0) {
    return <Vacio texto="Todavía no tienes libros virtuales. Pídelos desde el catálogo." />;
  }

  return (
    <div>
      {mensaje && <div className="info-box" style={{ marginBottom: 12 }}>{mensaje}</div>}
      <div className={compacta ? 'mlv-grid' : 'mlv-grid mlv-grid-amplio'}>
        {accesos.map((a) => {
          const vigente = a.estado === 'PAGADO' && !a.vencido;
          const plomizo = a.vencido || a.estado === 'CANCELADO';
          return (
            <div key={a.id} className={'mlv-card' + (plomizo ? ' mlv-plomo' : '')}>
              <Portada libro={{ id: a.id_libro, titulo: a.titulo, anio_publicacion: null, url_imagen: a.url_imagen }} alto={120} />
              <div className="mlv-info">
                <div className="mlv-titulo">{a.titulo}</div>
                <div className="acciones" style={{ margin: '6px 0' }}>
                  <Estado valor={a.tipo} mapa={tipoAccesoVirtual} />
                  <Estado valor={a.estado} mapa={estadoAccesoVirtual} />
                  {a.estado_comprobante && (
                    <Estado valor={a.estado_comprobante} mapa={estadoComprobante} />
                  )}
                </div>
                <div className="pick-sub">
                  {a.tipo === 'ALQUILER'
                    ? a.dias_alquiler
                      ? `${a.dias_alquiler} días · ${a.dias_restantes > 0 ? `te quedan ${a.dias_restantes}` : 'vencido'}`
                      : '—'
                    : `Compra${a.puede_descargar ? ' · con descarga' : ''}`}
                </div>
                <div className="pick-sub">
                  {fmtSoles(a.precio)} · {a.fecha_fin ? `hasta ${fmtFecha(a.fecha_fin)}` : 'sin vencimiento'}
                </div>

                {plomizo ? (
                  <>
                    <div className="mlv-vencido">
                      {a.vencido ? 'Este alquiler ya venció' : 'Este pedido fue cancelado'}
                    </div>
                    <button className="btn-ghost btn-sm" onClick={() => navegar(`/libros?q=${encodeURIComponent(a.titulo)}`)}>
                      Reservar de nuevo
                    </button>
                  </>
                ) : vigente ? (
                  <div className="acciones" style={{ marginTop: 8 }}>
                    <button className="btn-primary btn-sm" onClick={() => abrir(a)} disabled={enviando}>Leer</button>
                    {a.puede_descargar && (
                      <button className="btn-ghost btn-sm" onClick={() => abrir(a, true)} disabled={enviando}>Descargar PDF</button>
                    )}
                  </div>
                ) : (
                  <div className="info-box" style={{ marginTop: 8 }}>
                    {a.estado_comprobante === 'PENDIENTE'
                      ? 'Comprobante en revisión: el personal lo está mirando. Te avisamos por notificación.'
                      : a.estado_comprobante === 'DENEGADO'
                        ? 'No aceptamos tu comprobante. Vuelve a pedir el libro y sube otra captura.'
                        : a.metodo_pago === 'EFECTIVO'
                          ? 'Falta pagar en la biblioteca: muestra tu código en el mostrador.'
                          : 'Falta pagar: pide el libro desde el catálogo, paga con Yape y sube la captura.'}
                  </div>
                )}
              </div>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function MisLibrosVirtuales() {
  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Mis libros virtuales</h1>
          <p>Los que alquilaste y los que compraste</p>
        </div>
      </div>
      <ListaMisLibrosVirtuales />
    </div>
  );
}

export default MisLibrosVirtuales;
