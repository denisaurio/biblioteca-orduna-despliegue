// Réplica del prototipo Figma "CONFIGURACION" (RF13), solo gerente:
// pestañas General / Multas y Reservas / Almacenamiento, vista previa y "Guardar cambios".
// Los cambios de tarifa y días quedan versionados (configuracion_multas): no son retroactivos.
import { useEffect, useState } from 'react';
import { getConfiguracion, actualizarConfiguracion, getHistorialConfiguracion } from '../../api/configuracionApi.js';
import { getEstadoAlmacenamiento, probarEscrituraAlmacenamiento, prepararAlmacenamiento } from '../../api/almacenamientoApi.js';
import { useApi, useAccion, useRefrescoTiempoReal } from '../../hooks.js';
import { Cargando } from '../../components/common/Cargando.jsx';
import Badge from '../../components/common/Badge.jsx';
import { fmtFechaHora, fmtSoles } from '../../utils/formato.js';
import './Configuracion.css';

function Configuracion() {
  const [tab, setTab] = useState('general');
  const [form, setForm] = useState(null);
  const { datos, cargando } = useApi(() => getConfiguracion(), []);
  const { datos: historial, recargar: recargarHistorial } = useApi(() => getHistorialConfiguracion(), [], []);
  // Si otro gerente cambia la configuración, se refresca el historial (no el formulario,
  // para no borrar lo que este usuario esté escribiendo).
  useRefrescoTiempoReal(['configuracion'], () => { recargarHistorial({ silencioso: true }); });
  const { ejecutar, enviando } = useAccion();

  useEffect(() => {
    if (datos) setForm({ ...datos, hora_apertura: datos.hora_apertura?.slice(0, 5) || '', hora_cierre: datos.hora_cierre?.slice(0, 5) || '' });
  }, [datos]);

  if (cargando || !form) return <Cargando />;

  const set = (campo) => (e) => setForm((f) => ({ ...f, [campo]: e.target.value }));

  const guardar = async () => {
    const datosEnviar = {
      nombre_biblioteca: form.nombre_biblioteca,
      correo_contacto: form.correo_contacto || null,
      telefono_contacto: form.telefono_contacto,
      hora_apertura: form.hora_apertura || null,
      hora_cierre: form.hora_cierre || null,
      dias_max: Number(form.dias_max),
      tarifa_multa_diaria: Number(form.tarifa_multa_diaria),
      dias_retiro_reserva: Number(form.dias_retiro_reserva),
    };
    const r = await ejecutar(() => actualizarConfiguracion(datosEnviar), 'Configuración guardada');
    if (r) recargarHistorial();
  };

  return (
    <div>
      <div className="page-head">
        <div><h1>Configuración</h1><p>Parámetros generales del sistema (RF13).</p></div>
      </div>

      <div className="config-tabs">
        <button className={tab === 'general' ? 'active' : ''} onClick={() => setTab('general')}>General</button>
        <button className={tab === 'multas' ? 'active' : ''} onClick={() => setTab('multas')}>Multas y Reservas</button>
        <button className={tab === 'almacenamiento' ? 'active' : ''} onClick={() => setTab('almacenamiento')}>Almacenamiento</button>
      </div>

      {tab !== 'almacenamiento' && (
      <div className="config-grid">
        <div className="card config-form">
          {tab === 'general' ? (
            <>
              <h3>Configuración general</h3>
              <div className="form-row">
                <div><label>Nombre de la biblioteca</label><input value={form.nombre_biblioteca || ''} onChange={set('nombre_biblioteca')} /></div>
                <div><label>Email de contacto</label><input type="email" value={form.correo_contacto || ''} onChange={set('correo_contacto')} /></div>
              </div>
              <div className="form-row">
                <div><label>Teléfono</label><input value={form.telefono_contacto || ''} onChange={set('telefono_contacto')} /></div>
                <div><label>Días máximo de préstamo</label><input type="number" min="1" value={form.dias_max} onChange={set('dias_max')} /></div>
              </div>
              <label className="horario-label">Horario de apertura</label>
              <div className="form-row">
                <div><label className="sub-label">Apertura</label><input type="time" value={form.hora_apertura} onChange={set('hora_apertura')} /></div>
                <div><label className="sub-label">Cierre</label><input type="time" value={form.hora_cierre} onChange={set('hora_cierre')} /></div>
              </div>
            </>
          ) : (
            <>
              <h3>Multas y reservas</h3>
              <div className="form-row">
                <div><label>Tarifa diaria de multa (S/)</label><input type="number" step="0.10" min="0" value={form.tarifa_multa_diaria} onChange={set('tarifa_multa_diaria')} /></div>
                <div><label>Días máximo de préstamo</label><input type="number" min="1" value={form.dias_max} onChange={set('dias_max')} /></div>
              </div>
              <div className="form-row">
                <div><label>Días para retirar una reserva</label><input type="number" min="1" value={form.dias_retiro_reserva} onChange={set('dias_retiro_reserva')} /></div>
                <div />
              </div>
              <p className="hint">Los cambios quedan versionados con fecha de vigencia y no afectan préstamos o multas ya calculados.</p>
            </>
          )}
          <button className="btn-primary" onClick={guardar} disabled={enviando}>{enviando ? 'Guardando...' : 'Guardar cambios'}</button>
        </div>

        <div className="card config-preview">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6">
            <path d="M4 5.5A2.5 2.5 0 0 1 6.5 3H20v16H6.5A2.5 2.5 0 0 0 4 21.5V5.5Z" />
          </svg>
          <div>{form.nombre_biblioteca}</div>
          <small style={{ color: 'var(--muted)' }}>
            {form.hora_apertura && form.hora_cierre ? `Abierto de ${form.hora_apertura} a ${form.hora_cierre}` : ''}
          </small>
        </div>
      </div>
      )}

      {tab === 'multas' && (
        <>
          <h3 className="seccion-titulo" style={{ marginTop: 26 }}>Historial de vigencias</h3>
          <div className="card table-card">
            <table>
              <thead><tr><th>Vigente desde</th><th>Hasta</th><th>Tarifa diaria</th><th>Días de préstamo</th><th>Días para retirar</th><th>Modificado por</th></tr></thead>
              <tbody>
                {historial.map((h) => (
                  <tr key={h.id}>
                    <td className="mono">{fmtFechaHora(h.fecha_vigencia_desde)}</td>
                    <td className="mono">{h.fecha_vigencia_hasta ? fmtFechaHora(h.fecha_vigencia_hasta) : <b style={{ color: 'var(--green-text)' }}>Vigente</b>}</td>
                    <td className="mono">{fmtSoles(h.tarifa_multa_diaria)}</td>
                    <td className="mono">{h.dias_max}</td>
                    <td className="mono">{h.dias_retiro_reserva}</td>
                    <td>{h.modificado_por || 'Configuración inicial'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </>
      )}

      {tab === 'almacenamiento' && <Almacenamiento />}
    </div>
  );
}

/**
 * Estado del almacenamiento de archivos (Cloudflare R2): credenciales, buckets y
 * URL publica de las portadas. Sirve para saber que falta antes de intentar
 * subir una portada o un PDF.
 */
function Almacenamiento() {
  const { datos: estado, setDatos, cargando, recargar } = useApi(() => getEstadoAlmacenamiento(), [], null);
  const { ejecutar, enviando } = useAccion();
  const [prueba, setPrueba] = useState(null);
  const [preparado, setPreparado] = useState(null);

  const probar = async () => {
    const r = await ejecutar(() => probarEscrituraAlmacenamiento(), 'Prueba de escritura terminada');
    if (r) {
      setPrueba(r);
      recargar();
    }
  };

  const preparar = async () => {
    const r = await ejecutar(() => prepararAlmacenamiento(), 'Almacenamiento preparado');
    if (r) {
      setPreparado(r);
      setDatos(r.estado);
    }
  };

  if (cargando) return <Cargando />;
  if (!estado) {
    return <div className="info-box">No se pudo consultar el estado del almacenamiento.</div>;
  }

  const buckets = [
    { ...estado.bucket_portadas, uso: 'Portadas (público)' },
    { ...estado.bucket_pdfs, uso: 'PDF y comprobantes (privado)' },
  ];

  return (
    <div className="card config-form">
      <h3>Almacenamiento de archivos (Cloudflare R2)</h3>

      {!estado.configurado && (
        <div className="error-form">
          Faltan las credenciales de R2 en el archivo <b>.env</b>: R2_ACCOUNT_ID, R2_ACCESS_KEY_ID y
          R2_SECRET_ACCESS_KEY. Después de tocarlas hay que reiniciar el backend.
        </div>
      )}

      <div className="card table-card" style={{ marginBottom: 18 }}>
        <table>
          <thead>
            <tr><th>Bucket</th><th>Uso</th><th>Existe</th><th>Accesible</th><th>Detalle</th></tr>
          </thead>
          <tbody>
            {buckets.map((b) => (
              <tr key={b.nombre}>
                <td className="mono">{b.nombre}</td>
                <td>{b.uso}</td>
                <td><Badge color={b.existe ? 'green' : 'amber'}>{b.existe ? 'Sí' : 'No'}</Badge></td>
                <td><Badge color={b.accesible ? 'green' : 'red'}>{b.accesible ? 'Sí' : 'No'}</Badge></td>
                <td>{b.detalle}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="form-row">
        <div>
          <label>URL pública de las portadas</label>
          <input readOnly value={estado.url_publica || '(sin configurar)'} />
        </div>
        <div>
          <label>De dónde sale</label>
          <input readOnly value={estado.origen_url_publica || '—'} />
        </div>
      </div>

      {!estado.url_publica_configurada && (
        <div className="info-box" style={{ marginBottom: 14 }}>
          Las portadas <b>sí se ven</b>: mientras no haya URL pública, las sirve el propio backend
          (<span className="mono">/api/archivos/portadas/…</span>) con caché de un año en el navegador.
          Para el VPS conviene que las entregue Cloudflare directamente: activa la
          <b> Public Development URL</b> del bucket de portadas (Cloudflare → R2 → bucket → Settings), cópiala
          al <b>.env</b> como <span className="mono">R2_URL_PUBLICO_PORTADAS</span> y reinicia el backend.
          Las portadas ya guardadas pasan solas a la URL nueva.
        </div>
      )}

      <div className="acciones">
        <button className="btn-primary" onClick={preparar} disabled={enviando}>
          {enviando ? 'Preparando...' : 'Preparar almacenamiento'}
        </button>
        <button className="btn-ghost" onClick={probar} disabled={enviando}>
          {enviando ? 'Probando...' : 'Probar escritura'}
        </button>
      </div>

      {preparado && (
        <div className="info-box" style={{ marginTop: 14 }}>
          {preparado.creados && preparado.creados.length
            ? `Se crearon en Cloudflare R2: ${preparado.creados.join(', ')}.`
            : 'Los buckets ya existían: no hubo nada que crear.'}
        </div>
      )}

      {prueba && (
        <div className={prueba.escritura === 'OK' ? 'info-box' : 'error-form'} style={{ marginTop: 14 }}>
          {prueba.detalle}
        </div>
      )}

      <p className="hint">
        «Preparar almacenamiento» crea en R2 los buckets que falten (el de portadas y el privado de
        los PDF y comprobantes). La prueba de escritura sube un archivo vacío al bucket de portadas
        (0 bytes) y no se ve en el catálogo. Para activar la URL pública de las portadas hay que ir a
        Cloudflare → R2 → bucket → Settings → Public Development URL.
      </p>
    </div>
  );
}

export default Configuracion;
