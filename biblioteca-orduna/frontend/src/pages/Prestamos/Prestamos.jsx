// Réplica del prototipo Figma "PRESTAMOS" (RF06): asistente Usuario → Libro → Confirmar.
// Plazo: FIJO (los días de la configuración) o PERSONALIZADO (el personal elige los días).
// Multa por día de retraso: la de la configuración o una personalizada. Los dos tipos generan multa.
// Un libro sin copias libres sale en gris con la fecha en que vuelve la primera copia prestada.
// Reglas (las valida el backend): solo se presta si hay ejemplar disponible, el lector no puede
// tener sanción activa ni multas pendientes. El bibliotecario se toma del JWT.
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { getUsuarios } from '../../api/usuariosApi.js';
import { getLibros, buscarLibros } from '../../api/librosApi.js';
import { crearPrestamo } from '../../api/prestamosApi.js';
import { getConfiguracion } from '../../api/configuracionApi.js';
import { useApi, useAccion, useDebounce, useRefrescoTiempoReal } from '../../hooks.js';
import Estado from '../../components/common/Estado.jsx';
import { Cargando, Vacio } from '../../components/common/Cargando.jsx';
import { estadoLibro, estadoUsuario, nombreTipoPrestamo } from '../../utils/estados.js';
import { codigo, fmtFecha, fmtFechaHora, fmtSoles, hoyISO, iniciales, nombreTipoUsuario } from '../../utils/formato.js';
import './Prestamos.css';

const pasos = ['Usuario', 'Libro', 'Confirmar'];

function Prestamos() {
  const [paso, setPaso] = useState(1);
  const [usuarioSel, setUsuarioSel] = useState(null);
  const [libroSel, setLibroSel] = useState(null);
  const [tipo, setTipo] = useState('FECHA_FIJA');
  const [diasPersonalizados, setDiasPersonalizados] = useState('');
  const [modoMulta, setModoMulta] = useState('CONFIG');
  const [tarifaPersonalizada, setTarifaPersonalizada] = useState('');
  const [buscaUsuario, setBuscaUsuario] = useState('');
  const [buscaLibro, setBuscaLibro] = useState('');
  const [resultado, setResultado] = useState(null);
  const qU = useDebounce(buscaUsuario);
  const qL = useDebounce(buscaLibro);
  const { ejecutar, enviando } = useAccion();

  const { datos: usuarios, cargando: cargandoU, recargar: recargarUsuarios } = useApi(
    () => getUsuarios({ q: qU, rol: 'LECTOR', tamanio: 6 }), [qU], { contenido: [] });
  const { datos: libros, cargando: cargandoL, recargar: recargarLibros } = useApi(
    () => (qL.trim() ? buscarLibros(qL.trim()) : getLibros()), [qL], []);
  const { datos: config } = useApi(() => getConfiguracion(), [], null);

  // Si otro bibliotecario presta o devuelve, cambian los libros disponibles y los lectores.
  useRefrescoTiempoReal(['prestamos', 'devoluciones', 'reservas'], () => {
    recargarUsuarios({ silencioso: true });
    recargarLibros({ silencioso: true });
  });

  const diasMax = config?.dias_max ?? 7;
  const tarifaConfig = Number(config?.tarifa_multa_diaria ?? 1);

  // Plazo elegido: fijo (configuración) o personalizado (1 a 365 días).
  const diasElegidos = tipo === 'PERSONALIZADO' ? Number(diasPersonalizados) : diasMax;
  const diasValidos = Number.isInteger(diasElegidos) && diasElegidos >= 1 && diasElegidos <= 365;
  // Multa por día: la de la configuración o una personalizada.
  const tarifa = modoMulta === 'PERSONALIZADA' ? Number(tarifaPersonalizada) : tarifaConfig;
  const tarifaValida = modoMulta === 'CONFIG' || (tarifaPersonalizada !== '' && tarifa >= 0);
  const puedeConfirmar = paso === 3 && diasValidos && tarifaValida;

  const fechaDevolucion = diasValidos ? fmtFecha(hoyISO(diasElegidos)) : '—';
  const horaActual = new Date().toTimeString().slice(0, 5);

  const confirmar = async () => {
    const r = await ejecutar(
      () => crearPrestamo({
        id_libro: libroSel.id,
        id_usuario_lector: usuarioSel.id,
        tipo_prestamo: tipo,
        dias_prestamo: tipo === 'PERSONALIZADO' ? diasElegidos : null,
        tarifa_multa_diaria: modoMulta === 'PERSONALIZADA' ? tarifa : null,
      }),
      'Préstamo registrado'
    );
    if (r) setResultado(r);
  };

  const reiniciar = () => {
    setPaso(1); setUsuarioSel(null); setLibroSel(null); setTipo('FECHA_FIJA'); setResultado(null);
    setDiasPersonalizados(''); setModoMulta('CONFIG'); setTarifaPersonalizada('');
    setBuscaUsuario(''); setBuscaLibro('');
  };

  return (
    <div>
      <div className="page-head">
        <div><h1>Nuevo préstamo</h1><p>Complete los pasos para registrar un nuevo préstamo.</p></div>
        <Link to="/devoluciones" className="btn-ghost">Ver préstamos activos</Link>
      </div>

      <div className="card wizard-steps-card">
        <div className="wizard-steps">
          {pasos.map((p, i) => (
            <div key={p} className={'wizard-step' + (paso === i + 1 ? ' active' : paso > i + 1 || resultado ? ' done' : '')}>
              <span className="num">{paso > i + 1 || resultado ? '✓' : i + 1}</span>{p}
            </div>
          ))}
        </div>
      </div>

      <div className="wizard-grid">
        <div className="card wizard-panel">
          {resultado ? (
            <>
              <h3>✓ Préstamo registrado</h3>
              <p className="hint">Entregue el ejemplar indicado al lector.</p>
              <dl className="detalle">
                <dt>N.º de préstamo</dt><dd className="mono">{codigo('P', resultado.id)}</dd>
                <dt>Ejemplar</dt><dd className="mono">{resultado.codigo_inventario}</dd>
                <dt>Lector</dt><dd>{resultado.nombre_lector}</dd>
                <dt>Tipo</dt><dd>{nombreTipoPrestamo[resultado.tipo_prestamo] || resultado.tipo_prestamo} ({resultado.dias_prestamo} día(s))</dd>
                <dt>Prestado el</dt><dd className="mono">{fmtFechaHora(resultado.fecha_prestamo)}</dd>
                <dt>Devolver hasta</dt><dd>{resultado.fecha_vencimiento ? fmtFecha(resultado.fecha_vencimiento) : '—'}</dd>
                <dt>Multa por retraso</dt><dd className="mono">{fmtSoles(resultado.tarifa_multa_diaria)} por día{resultado.tarifa_personalizada ? ' (personalizada)' : ''}</dd>
                <dt>Precio del ejemplar</dt><dd className="mono">{resultado.precio_copia != null ? fmtSoles(resultado.precio_copia) : '—'}</dd>
              </dl>
              <button className="btn-primary" style={{ marginTop: 18 }} onClick={reiniciar}>Registrar otro préstamo</button>
            </>
          ) : (
            <>
              <h3>Nuevo préstamo</h3>
              <p className="hint">Selecciona el usuario y el libro que deseas prestar.</p>

              {paso === 1 && (
                <>
                  <label>Buscar usuario</label>
                  <input className="wizard-input" placeholder="Nombre, DNI o correo del lector" value={buscaUsuario}
                         onChange={(e) => setBuscaUsuario(e.target.value)} autoFocus />
                  <div className="pick-list">
                    {cargandoU ? <Cargando /> : usuarios.contenido.length === 0 ? <Vacio texto="No se encontraron lectores." /> :
                      usuarios.contenido.map((u) => {
                        const bloqueado = u.estado !== 'ACTIVO';
                        return (
                          <div key={u.id}
                               className={'pick-item' + (usuarioSel?.id === u.id ? ' selected' : '') + (bloqueado ? ' bloqueado' : '')}
                               onClick={() => !bloqueado && setUsuarioSel(u)}>
                            <span className="pick-avatar">{iniciales(`${u.nombre} ${u.apellido}`)}</span>
                            <div>
                              <div className="pick-title">{u.nombre} {u.apellido}</div>
                              <div className="pick-sub">Código {codigo('U', u.id)} · {nombreTipoUsuario[u.tipo_usuario]} · {u.prestamos_activos} préstamo(s)</div>
                            </div>
                            {bloqueado ? <span style={{ marginLeft: 'auto' }}><Estado valor={u.estado} mapa={estadoUsuario} /></span>
                              : usuarioSel?.id === u.id && <span className="check">✓</span>}
                          </div>
                        );
                      })}
                  </div>
                  <button className="btn-primary" style={{ marginTop: 16 }} disabled={!usuarioSel} onClick={() => setPaso(2)}>Continuar</button>
                </>
              )}

              {paso === 2 && (
                <>
                  <label>Buscar libro</label>
                  <input className="wizard-input" placeholder="Título, autor o ISBN" value={buscaLibro}
                         onChange={(e) => setBuscaLibro(e.target.value)} autoFocus />
                  <div className="pick-list">
                    {cargandoL ? <Cargando /> : libros.length === 0 ? <Vacio texto="No se encontraron libros." /> :
                      libros.map((l) => {
                        // Sin copias libres: en gris y con la fecha en que vuelve la primera prestada.
                        const ocupado = l.ejemplares_total > 0 && l.ejemplares_disponibles === 0;
                        return (
                          <div key={l.id}
                               className={'pick-item' + (libroSel?.id === l.id ? ' selected' : '')
                                 + (l.ejemplares_total === 0 ? ' bloqueado' : '') + (ocupado ? ' ocupado' : '')}
                               onClick={() => l.ejemplares_total > 0 && setLibroSel(l)}>
                            <span className="pick-avatar book">📖</span>
                            <div>
                              <div className="pick-title">{l.titulo}</div>
                              <div className="pick-sub">{l.autores.map((a) => a.nombre).join(', ')} · {l.ejemplares_disponibles}/{l.ejemplares_total} disponibles</div>
                              {ocupado && (
                                <div className="pick-devolucion">
                                  {l.proxima_devolucion
                                    ? <>Reservado · se devuelve el <b>{fmtFecha(l.proxima_devolucion)}</b></>
                                    : 'Reservado · apartado para un lector de la cola'}
                                </div>
                              )}
                            </div>
                            {libroSel?.id === l.id ? <span className="check">✓</span>
                              : <span style={{ marginLeft: 'auto' }}><Estado valor={l.estado} mapa={estadoLibro} /></span>}
                          </div>
                        );
                      })}
                  </div>
                  {libroSel && libroSel.ejemplares_disponibles === 0 && (
                    <div className="info-box" style={{ marginTop: 12 }}>
                      No hay ejemplares libres. Solo se podrá prestar si el lector tiene este libro apartado por una reserva.
                    </div>
                  )}
                  <div style={{ display: 'flex', gap: 10, marginTop: 16 }}>
                    <button className="btn-ghost" onClick={() => setPaso(1)}>Atrás</button>
                    <button className="btn-primary" disabled={!libroSel} onClick={() => setPaso(3)}>Continuar</button>
                  </div>
                </>
              )}

              {paso === 3 && (
                <>
                  <label>Tipo de préstamo</label>
                  <div className="tipo-prestamo">
                    <label className={tipo === 'FECHA_FIJA' ? 'on' : ''}>
                      <input type="radio" checked={tipo === 'FECHA_FIJA'} onChange={() => setTipo('FECHA_FIJA')} />
                      <div><b>Fijo</b><small>Devolver en {diasMax} días (según la configuración). Genera multa si se retrasa.</small></div>
                    </label>
                    <label className={tipo === 'PERSONALIZADO' ? 'on' : ''}>
                      <input type="radio" checked={tipo === 'PERSONALIZADO'} onChange={() => setTipo('PERSONALIZADO')} />
                      <div style={{ flex: 1 }}>
                        <b>Personalizado</b><small>Tú eliges cuántos días se lleva la copia. También genera multa si se retrasa.</small>
                        {tipo === 'PERSONALIZADO' && (
                          <div className="tipo-extra">
                            <input type="number" min="1" max="365" step="1" placeholder="Días" value={diasPersonalizados}
                                   onChange={(e) => setDiasPersonalizados(e.target.value)} autoFocus />
                            <span>día(s){diasValidos ? ` · devuelve el ${fmtFecha(hoyISO(diasElegidos))}` : ' (de 1 a 365)'}</span>
                          </div>
                        )}
                      </div>
                    </label>
                  </div>

                  <label style={{ marginTop: 18 }}>Multa por retraso</label>
                  <div className="tipo-prestamo">
                    <label className={modoMulta === 'CONFIG' ? 'on' : ''}>
                      <input type="radio" checked={modoMulta === 'CONFIG'} onChange={() => setModoMulta('CONFIG')} />
                      <div><b>Según la configuración</b><small>{fmtSoles(tarifaConfig)} por cada día de retraso.</small></div>
                    </label>
                    <label className={modoMulta === 'PERSONALIZADA' ? 'on' : ''}>
                      <input type="radio" checked={modoMulta === 'PERSONALIZADA'} onChange={() => setModoMulta('PERSONALIZADA')} />
                      <div style={{ flex: 1 }}>
                        <b>Personalizada</b><small>Tú eliges cuánto se cobra por cada día de retraso.</small>
                        {modoMulta === 'PERSONALIZADA' && (
                          <div className="tipo-extra">
                            <span>S/</span>
                            <input type="number" min="0" step="0.50" placeholder="0.00" value={tarifaPersonalizada}
                                   onChange={(e) => setTarifaPersonalizada(e.target.value)} autoFocus />
                            <span>por día</span>
                          </div>
                        )}
                      </div>
                    </label>
                  </div>
                  {diasValidos && tarifaValida && (
                    <div className="info-box" style={{ marginTop: 12 }}>
                      Si devuelve el libro después del <b>{fechaDevolucion}</b> pagará <b>{fmtSoles(tarifa)}</b> por cada día de retraso
                      (por ejemplo, 3 días = {fmtSoles(tarifa * 3)}).
                    </div>
                  )}
                  <div style={{ display: 'flex', gap: 10, marginTop: 18 }}>
                    <button className="btn-ghost" onClick={() => setPaso(2)}>Atrás</button>
                  </div>
                </>
              )}
            </>
          )}
        </div>

        <div className="card wizard-summary">
          <h3>Resumen del préstamo</h3>
          <p className="hint">Revise los datos antes de confirmar.</p>
          <div className="summary-row"><span>Usuario</span><b>{usuarioSel ? <>{usuarioSel.nombre} {usuarioSel.apellido}<br /><small>Código {codigo('U', usuarioSel.id)}</small></> : '—'}</b></div>
          <div className="summary-row"><span>Libro</span><b>{libroSel ? <>{libroSel.titulo}<br /><small>{libroSel.autores.map((a) => a.nombre).join(', ')}</small></> : '—'}</b></div>
          <div className="summary-row"><span>Fecha préstamo</span><b>{fmtFecha(hoyISO())} <small>{horaActual}</small></b></div>
          <div className="summary-row"><span>Tipo</span><b>{nombreTipoPrestamo[tipo]}{diasValidos ? <><br /><small>{diasElegidos} día(s)</small></> : ''}</b></div>
          <div className="summary-row"><span>Fecha de devolución</span><b>{fechaDevolucion}</b></div>
          <div className="summary-row">
            <span>Multa por día de retraso</span>
            <b>{tarifaValida ? fmtSoles(tarifa) : '—'}{modoMulta === 'PERSONALIZADA' ? <><br /><small>Personalizada</small></> : ''}</b>
          </div>
          <div className="summary-row">
            <span>Precio del ejemplar</span>
            <b>{libroSel?.precio_compra != null ? fmtSoles(libroSel.precio_compra) : '—'}</b>
          </div>
          {!resultado && (
            <button className="btn-primary" style={{ width: '100%', justifyContent: 'center', marginTop: 16 }}
                    disabled={!puedeConfirmar || enviando} onClick={confirmar}>
              {enviando ? 'Registrando...' : 'Confirmar préstamo'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default Prestamos;
