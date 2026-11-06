// Réplica del prototipo Figma "PRESTAMOS" (RF06): asistente Usuario → Libro → Confirmar.
// Reglas (las valida el backend): solo se presta si hay ejemplar disponible, el lector no puede
// tener sanción activa ni multas pendientes. El bibliotecario se toma del JWT.
import { useState } from 'react';
import { Link } from 'react-router-dom';
import { getUsuarios } from '../../api/usuariosApi.js';
import { getLibros, buscarLibros } from '../../api/librosApi.js';
import { crearPrestamo } from '../../api/prestamosApi.js';
import { getConfiguracion } from '../../api/configuracionApi.js';
import { useApi, useAccion, useDebounce } from '../../hooks.js';
import Estado from '../../components/common/Estado.jsx';
import { Cargando, Vacio } from '../../components/common/Cargando.jsx';
import { estadoLibro, estadoUsuario } from '../../utils/estados.js';
import { codigo, fmtFecha, fmtSoles, hoyISO, iniciales, nombreTipoUsuario } from '../../utils/formato.js';
import './Prestamos.css';

const pasos = ['Usuario', 'Libro', 'Confirmar'];

function Prestamos() {
  const [paso, setPaso] = useState(1);
  const [usuarioSel, setUsuarioSel] = useState(null);
  const [libroSel, setLibroSel] = useState(null);
  const [tipo, setTipo] = useState('FECHA_FIJA');
  const [buscaUsuario, setBuscaUsuario] = useState('');
  const [buscaLibro, setBuscaLibro] = useState('');
  const [resultado, setResultado] = useState(null);
  const qU = useDebounce(buscaUsuario);
  const qL = useDebounce(buscaLibro);
  const { ejecutar, enviando } = useAccion();

  const { datos: usuarios, cargando: cargandoU } = useApi(
    () => getUsuarios({ q: qU, rol: 'LECTOR', tamanio: 6 }), [qU], { contenido: [] });
  const { datos: libros, cargando: cargandoL } = useApi(
    () => (qL.trim() ? buscarLibros(qL.trim()) : getLibros()), [qL], []);
  const { datos: config } = useApi(() => getConfiguracion(), [], null);

  const diasMax = config?.dias_max ?? 7;
  const fechaDevolucion = tipo === 'ABIERTO' ? 'Sin fecha (préstamo abierto)' : fmtFecha(hoyISO(diasMax));

  const confirmar = async () => {
    const r = await ejecutar(
      () => crearPrestamo({ id_libro: libroSel.id, id_usuario_lector: usuarioSel.id, tipo_prestamo: tipo }),
      'Préstamo registrado'
    );
    if (r) setResultado(r);
  };

  const reiniciar = () => {
    setPaso(1); setUsuarioSel(null); setLibroSel(null); setTipo('FECHA_FIJA'); setResultado(null);
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
                <dt>Tipo</dt><dd>{resultado.tipo_prestamo === 'ABIERTO' ? 'Abierto (sin multa)' : 'Fecha fija'}</dd>
                <dt>Devolver hasta</dt><dd>{resultado.fecha_vencimiento ? fmtFecha(resultado.fecha_vencimiento) : '—'}</dd>
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
                      libros.map((l) => (
                        <div key={l.id} className={'pick-item' + (libroSel?.id === l.id ? ' selected' : '') + (l.ejemplares_total === 0 ? ' bloqueado' : '')}
                             onClick={() => l.ejemplares_total > 0 && setLibroSel(l)}>
                          <span className="pick-avatar book">📖</span>
                          <div>
                            <div className="pick-title">{l.titulo}</div>
                            <div className="pick-sub">{l.autores.map((a) => a.nombre).join(', ')} · {l.ejemplares_disponibles}/{l.ejemplares_total} disponibles</div>
                          </div>
                          {libroSel?.id === l.id ? <span className="check">✓</span>
                            : <span style={{ marginLeft: 'auto' }}><Estado valor={l.estado} mapa={estadoLibro} /></span>}
                        </div>
                      ))}
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
                      <div><b>Fecha fija</b><small>Devolver en {diasMax} días. Genera multa si se retrasa.</small></div>
                    </label>
                    <label className={tipo === 'ABIERTO' ? 'on' : ''}>
                      <input type="radio" checked={tipo === 'ABIERTO'} onChange={() => setTipo('ABIERTO')} />
                      <div><b>Abierto</b><small>Sin fecha límite. Nunca genera multa.</small></div>
                    </label>
                  </div>
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
          <div className="summary-row"><span>Fecha préstamo</span><b>{fmtFecha(hoyISO())}</b></div>
          <div className="summary-row"><span>Fecha de devolución</span><b>{fechaDevolucion}</b></div>
          <div className="summary-row">
            <span>Precio del ejemplar</span>
            <b>{libroSel?.precio_compra != null ? fmtSoles(libroSel.precio_compra) : '—'}</b>
          </div>
          {!resultado && (
            <button className="btn-primary" style={{ width: '100%', justifyContent: 'center', marginTop: 16 }}
                    disabled={paso !== 3 || enviando} onClick={confirmar}>
              {enviando ? 'Registrando...' : 'Confirmar préstamo'}
            </button>
          )}
        </div>
      </div>
    </div>
  );
}

export default Prestamos;
