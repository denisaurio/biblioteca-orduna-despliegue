// Pago con Yape: el QR de Yape Negocio, el monto debajo y la subida de la captura.
//
// El QR es ESTATICO (no lleva el monto: Yape no tiene API publica con QR dinamico).
// El lector abre su app de Yape, teclea el monto que ve aqui, paga, saca una
// captura y la sube. El personal la revisa y la acepta o la deniega.
import { useState } from 'react';
import { API_URL } from '../../api/axiosClient.js';
import Campo from './Campo.jsx';
import { fmtSoles } from '../../utils/formato.js';
import './QrYape.css';

const URL_QR = `${API_URL}/pagos/yape/qr`;

/**
 * @param monto      monto a pagar (se muestra debajo del QR)
 * @param enviado    true si el lector ya subio una captura (se muestra el estado)
 * @param estado     texto opcional con el estado del comprobante
 * @param enviando   true mientras se sube la captura
 * @param onEnviar   callback con { archivo, referencia }
 */
function QrYape({ monto, enviado = false, estado = '', enviando = false, onEnviar }) {
  const [archivo, setArchivo] = useState(null);
  const [referencia, setReferencia] = useState('');

  const enviar = () => {
    if (!archivo) return;
    onEnviar({ archivo, referencia: referencia.trim() });
  };

  return (
    <div className="qr-yape">
      <div className="qr-yape-monto">{fmtSoles(monto)}</div>

      <img className="qr-yape-img" src={URL_QR} alt="Código QR de Yape Negocio" />

      <div className="info-box qr-yape-pasos">
        <strong>Paga así:</strong>
        <ol>
          <li>Abre la app de Yape y elige <strong>Escanear QR</strong>.</li>
          <li>Escanea este código y escribe el monto de arriba ({fmtSoles(monto)}).</li>
          <li>Confirma el pago y toma una captura de la pantalla.</li>
          <li>Sube la captura aquí: el personal la revisa y activa tu libro.</li>
        </ol>
      </div>

      {enviado ? (
        <div className="ok-form" style={{ marginTop: 12 }}>
          Comprobante enviado{estado ? `: ${estado}` : ''}. El personal lo revisa y te avisamos por
          notificación. Si lo aceptan, tu libro queda disponible al instante.
        </div>
      ) : (
        <>
          <Campo
            label="Código de operación de Yape (opcional)"
            ayuda="El número que sale en tu app Yape. Sirve para cotejar el pago."
            ancho={2}
          >
            <input
              value={referencia}
              onChange={(e) => setReferencia(e.target.value)}
              placeholder="Ej: 48219374651"
              maxLength={40}
            />
          </Campo>

          <Campo label="Captura de tu pago" ancho={2}>
            <input
              type="file"
              accept="image/png,image/jpeg,image/webp"
              onChange={(e) => setArchivo(e.target.files?.[0] || null)}
            />
          </Campo>

          <button
            type="button"
            className="btn-primary qr-yape-boton"
            onClick={enviar}
            disabled={enviando || !archivo}
          >
            {enviando ? 'Enviando...' : 'Enviar comprobante'}
          </button>
        </>
      )}
    </div>
  );
}

export default QrYape;
