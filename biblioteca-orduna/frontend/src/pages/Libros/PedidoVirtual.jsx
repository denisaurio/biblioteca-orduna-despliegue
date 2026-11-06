// Pedir un libro virtual (alquiler por días o compra) y con qué pagarlo.
//
// El lector elige cómo paga:
//   - Yape: ve el QR de Yape Negocio con el monto debajo, paga en su app, sube la
//     captura y el personal la aprueba (o la deniega y sube otra).
//   - Efectivo en la tienda: recibe un número de cola y un código de 24 horas para
//     el mostrador.
import { useState } from 'react';
import { misLibrosVirtuales, pedirLibroVirtual, subirComprobante } from '../../api/accesosVirtualesApi.js';
import { useAccion } from '../../hooks.js';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import QrYape from '../../components/common/QrYape.jsx';
import { fmtFechaHora, fmtSoles } from '../../utils/formato.js';
import { metodoPagoVirtual, tipoAccesoVirtual } from '../../utils/estados.js';

const DIAS = [1, 3, 7, 14, 30];

function PedidoVirtual({ libro, tipoInicial = 'ALQUILER', onClose, onListo }) {
  const [tipo, setTipo] = useState(tipoInicial);
  const [dias, setDias] = useState(7);
  const [metodo, setMetodo] = useState('YAPE');
  const [pedido, setPedido] = useState(null);
  const [comprobanteEnviado, setComprobanteEnviado] = useState(false);
  const { ejecutar, enviando } = useAccion();

  const total = tipo === 'ALQUILER'
    ? Number(libro.precio_alquiler_diario || 0) * dias
    : Number(libro.precio_compra || 0);

  const pedir = async () => {
    const r = await ejecutar(
      () => pedirLibroVirtual({
        id_libro: libro.id,
        tipo,
        dias_alquiler: tipo === 'ALQUILER' ? dias : null,
        metodo_pago: metodo,
      }),
      null
    );
    if (r && typeof r === 'object') setPedido(r);
  };

  /** El lector sube la captura de su pago con Yape. */
  const enviarComprobante = async ({ archivo, referencia }) => {
    const form = new FormData();
    form.append('comprobante', archivo);
    if (referencia) form.append('referencia_yape', referencia);
    const r = await ejecutar(() => subirComprobante(pedido.acceso.id, form), 'Comprobante enviado');
    if (r && typeof r === 'object') setComprobanteEnviado(true);
  };

  /**
   * El personal puede que todavia no lo haya revisado: se releen los libros del
   * lector y, si el pedido ya esta pagado, se avisa que el libro esta listo.
   */
  const actualizarEstado = async () => {
    const r = await ejecutar(() => misLibrosVirtuales(), null);
    if (!Array.isArray(r)) return;
    const acceso = r.find((a) => a.id === pedido.acceso.id);
    if (acceso) {
      setPedido({ ...pedido, acceso });
      if (acceso.estado === 'PAGADO') onListo?.();
    }
  };

  const acceso = pedido?.acceso;
  const pagado = acceso?.estado === 'PAGADO';

  return (
    <Modal
      titulo="Pedir libro virtual"
      subtitulo={libro.titulo}
      onClose={onClose}
      ancho={560}
      pie={
        <>
          <button className="btn-ghost" onClick={onClose}>Cerrar</button>
          {!pedido && (
            <button className="btn-primary" onClick={pedir} disabled={enviando}>
              {enviando ? 'Enviando...' : `Pedir por ${fmtSoles(total)}`}
            </button>
          )}
          {pedido && metodo === 'YAPE' && !pagado && (
            <button className="btn-primary" onClick={actualizarEstado} disabled={enviando}>
              {enviando ? 'Consultando...' : 'Actualizar estado'}
            </button>
          )}
        </>
      }
    >
      {pedido ? (
        <div>
          {pagado ? (
            <div className="ok-form">
              {acceso.tipo === 'COMPRA'
                ? '¡Felicidades, compra exitosa! Disfruta de tu libro pagado.'
                : `¡Felicidades! Disfruta tus ${acceso.dias_restantes} días restantes.`}
            </div>
          ) : metodo === 'YAPE' ? (
            <QrYape
              monto={acceso.precio}
              enviado={comprobanteEnviado}
              estado={acceso.estado_comprobante === 'DENEGADO' ? 'el anterior fue denegado' : ''}
              enviando={enviando}
              onEnviar={enviarComprobante}
            />
          ) : (
            <>
              <div className="ok-form">
                Pedido registrado. Paga en la biblioteca y te damos el libro al instante.
              </div>
              <dl className="detalle" style={{ marginTop: 14 }}>
                <dt>Número de cola</dt><dd className="mono" style={{ fontSize: 18 }}>{acceso.numero_cola}</dd>
                <dt>Código para el mostrador</dt><dd className="mono" style={{ fontSize: 18 }}>{acceso.codigo_retiro}</dd>
                <dt>El código vence</dt><dd>{fmtFechaHora(acceso.fecha_codigo_expira)}</dd>
                <dt>Monto a pagar</dt><dd>{fmtSoles(acceso.precio)}</dd>
              </dl>
              <div className="info-box" style={{ marginTop: 12 }}>
                Muestra el código en el mostrador. El bibliotecario puede cancelar la orden, cambiarla o cobrarla.
              </div>
            </>
          )}
        </div>
      ) : (
        <div className="form-grid">
          <Campo label="Cómo lo quieres" ancho={2}>
            <div className="chips">
              <span className={'chip' + (tipo === 'ALQUILER' ? ' on' : '')} onClick={() => setTipo('ALQUILER')}>
                Alquiler {tipoAccesoVirtual.ALQUILER[1]}
              </span>
              <span className={'chip' + (tipo === 'COMPRA' ? ' on' : '')} onClick={() => setTipo('COMPRA')}>
                Compra {tipoAccesoVirtual.COMPRA[1]}
              </span>
            </div>
          </Campo>

          {tipo === 'ALQUILER' && (
            <Campo label="Días de lectura" ancho={2} ayuda={`${fmtSoles(libro.precio_alquiler_diario)} por día`}>
              <div className="chips">
                {DIAS.map((d) => (
                  <span key={d} className={'chip' + (dias === d ? ' on' : '')} onClick={() => setDias(d)}>{d} días</span>
                ))}
              </div>
            </Campo>
          )}

          <Campo label="Forma de pago" ancho={2}>
            <div className="chips">
              {['YAPE', 'EFECTIVO'].map((m) => (
                <span key={m} className={'chip' + (metodo === m ? ' on' : '')} onClick={() => setMetodo(m)}>
                  {metodoPagoVirtual[m]}
                </span>
              ))}
            </div>
            <small>
              {metodo === 'EFECTIVO'
                ? 'Pagas en la biblioteca: te damos un número de cola y un código de 24 horas.'
                : 'Pagas con Yape: escaneas el QR, tomas una captura y la subes para que la revisemos.'}
            </small>
          </Campo>

          <div className="card" style={{ gridColumn: 'span 2', padding: '10px 14px' }}>
            <div className="summary-row" style={{ borderBottom: 'none', padding: 0 }}>
              <span>Total a pagar</span>
              <strong style={{ fontSize: 16 }}>{fmtSoles(total)}</strong>
            </div>
          </div>
        </div>
      )}
    </Modal>
  );
}

export default PedidoVirtual;
