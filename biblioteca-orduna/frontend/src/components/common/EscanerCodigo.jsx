// Escáner de códigos de barras con la cámara (celular o PC).
//
// Usa la API nativa del navegador (BarcodeDetector): no hace falta ninguna librería.
//   - En el celular: la cámara trasera lee el ISBN (y el código de inventario).
//   - En la PC: el lector físico de códigos actúa como teclado (escribe y pulsa Enter),
//     y también sirve la cámara de la propia computadora.
//
// La cámara solo se puede abrir en un sitio seguro: https o localhost.
import { useEffect, useRef, useState } from 'react';
import './EscanerCodigo.css';

/** Formatos típicos de un libro: ISBN (EAN-13/EAN-8), código de barras interno y QR. */
const FORMATOS = ['ean_13', 'ean_8', 'upc_a', 'code_128', 'qr_code'];

/** Cada cuánto se analiza un cuadro: suficiente para leer rápido sin calentar el celular. */
const INTERVALO_MS = 120;

function EscanerCodigo({ onCodigo, etiqueta = 'Cámara', formatos = FORMATOS, texto = 'Escanear con la cámara' }) {
  const [abierto, setAbierto] = useState(false);
  const [error, setError] = useState('');
  const videoRef = useRef(null);
  const streamRef = useRef(null);
  const loopRef = useRef(null);
  const ultimoRef = useRef(0);

  const soportado = typeof window !== 'undefined' && 'BarcodeDetector' in window;
  const camaraSegura = typeof navigator !== 'undefined' && !!navigator.mediaDevices?.getUserMedia;

  /** Cierra la cámara y libera la cámara del dispositivo. */
  const detener = () => {
    if (loopRef.current) {
      cancelAnimationFrame(loopRef.current);
      loopRef.current = null;
    }
    if (streamRef.current) {
      streamRef.current.getTracks().forEach((t) => t.stop());
      streamRef.current = null;
    }
    if (videoRef.current) videoRef.current.srcObject = null;
    setAbierto(false);
  };

  // Si el formulario se cierra con la cámara abierta, hay que soltarla.
  useEffect(() => () => {
    if (loopRef.current) cancelAnimationFrame(loopRef.current);
    if (streamRef.current) streamRef.current.getTracks().forEach((t) => t.stop());
  }, []);

  /** Bucle de lectura: cada ~120 ms se busca un código de barras en el cuadro. */
  const detectar = async (detector) => {
    if (!videoRef.current || !streamRef.current) return;
    const ahora = performance.now();
    if (ahora - ultimoRef.current >= INTERVALO_MS && videoRef.current.readyState >= 2) {
      ultimoRef.current = ahora;
      try {
        const encontrados = await detector.detect(videoRef.current);
        const valor = encontrados.find((c) => c.rawValue)?.rawValue;
        if (valor) {
          if (navigator.vibrate) navigator.vibrate(80);
          detener();
          onCodigo(valor.trim());
          return;
        }
      } catch {
        // El video puede no tener cuadro todavía: se vuelve a intentar en el siguiente.
      }
    }
    loopRef.current = requestAnimationFrame(() => detectar(detector));
  };

  const abrir = async () => {
    setError('');
    try {
      // Solo se piden los formatos que este navegador sabe leer (si no, el constructor falla).
      const disponibles = window.BarcodeDetector.getSupportedFormats
        ? await window.BarcodeDetector.getSupportedFormats()
        : formatos;
      const usar = formatos.filter((f) => disponibles.includes(f));
      if (usar.length === 0) {
        setError('Este navegador no lee códigos de barras de libros con la cámara. Usa el lector físico o escribe el código.');
        return;
      }
      const stream = await navigator.mediaDevices.getUserMedia({
        video: { facingMode: { ideal: 'environment' }, width: { ideal: 1280 }, height: { ideal: 720 } },
        audio: false,
      });
      streamRef.current = stream;
      setAbierto(true);
      const detector = new window.BarcodeDetector({ formats: usar });
      // El video se monta en este mismo render: se espera al siguiente cuadro.
      requestAnimationFrame(() => {
        if (!videoRef.current) return;
        videoRef.current.srcObject = stream;
        videoRef.current.play().catch(() => {});
        detectar(detector);
      });
    } catch (e) {
      setError(
        e?.name === 'NotAllowedError'
          ? 'El navegador no tiene permiso para usar la cámara: actívalo en el candado de la barra de direcciones.'
          : 'No se pudo abrir la cámara. Usa el lector físico o escribe el código a mano.'
      );
      detener();
    }
  };

  const avisoNoDisponible = !soportado
    ? 'Este navegador no puede leer códigos con la cámara (Firefox no lo soporta): usa Chrome o Edge, el lector físico o escribe el código a mano.'
    : 'La cámara solo se abre en un sitio seguro (https) o en localhost. Usa el lector físico o escribe el código.';

  if (!soportado || !camaraSegura) {
    return (
      <>
        <button type="button" className="btn-ghost" onClick={() => setError(avisoNoDisponible)} title={avisoNoDisponible}>
          {etiqueta === 'Cámara' ? '📷 Cámara' : texto}
        </button>
        {error && <div className="info-box" style={{ marginTop: 8, width: '100%' }}>{error}</div>}
      </>
    );
  }

  return (
    <>
      <button type="button" className="btn-ghost" onClick={abrir} disabled={abierto}>
        {etiqueta === 'Cámara' ? '📷 Cámara' : texto}
      </button>

      {abierto && (
        <div className="esc-lamina" onClick={detener}>
          <div className="esc-panel" onClick={(e) => e.stopPropagation()}>
            <div className="esc-marco">
              <video ref={videoRef} className="esc-video" muted playsInline />
              <div className="esc-guia" />
            </div>
            <p className="esc-texto">Acerca el código de barras al recuadro.</p>
            <button type="button" className="btn-ghost" onClick={detener}>Cerrar cámara</button>
          </div>
        </div>
      )}

      {error && (
        <div className="info-box" style={{ marginTop: 8, width: '100%' }}>{error}</div>
      )}
    </>
  );
}

export default EscanerCodigo;
