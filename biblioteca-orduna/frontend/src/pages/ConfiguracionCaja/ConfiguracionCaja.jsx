// Configuración del horario de la caja (solo gerente).
// Un día = una fila: encargado (bibliotecario), hora de inicio y hora de fin.
// Ejemplo: Lunes a Viernes 08:00-20:00 y Sábado 08:00-15:00.
// Dentro del rango el backend abre la caja sola con el encargado de hoy. Al
// llegar la hora de fin la cierra. La caja ya abierta conserva la hora con la
// que se abrió: cambiar este horario no la altera.
import { useEffect, useState } from 'react';
import {
  getEncargadosCaja,
  getHorariosCaja,
  guardarHorariosCaja,
} from '../../api/configuracionCajaApi.js';
import { useApi, useAccion, useRefrescoTiempoReal } from '../../hooks.js';
import { Cargando } from '../../components/common/Cargando.jsx';
import './ConfiguracionCaja.css';

const DIAS = [
  ['MONDAY', 'Lunes'],
  ['TUESDAY', 'Martes'],
  ['WEDNESDAY', 'Miércoles'],
  ['THURSDAY', 'Jueves'],
  ['FRIDAY', 'Viernes'],
  ['SATURDAY', 'Sábado'],
  ['SUNDAY', 'Domingo'],
];

const nombreDia = (codigo) => (DIAS.find(([c]) => c === codigo) || [codigo, codigo])[1];
const horaCorta = (valor) => (valor ? String(valor).slice(0, 5) : '');

function nuevaFila(dia) {
  return { dia_semana: dia, hora_inicio: '08:00', hora_fin: '20:00', id_encargado: '' };
}

function ConfiguracionCaja() {
  const { datos: horarios, cargando, recargar } = useApi(() => getHorariosCaja(), [], []);
  const { datos: encargados } = useApi(() => getEncargadosCaja(), [], []);
  const [filas, setFilas] = useState(null);
  const [error, setError] = useState('');
  const { ejecutar, enviando } = useAccion();
  useRefrescoTiempoReal(['cajas'], () => recargar({ silencioso: true }));

  useEffect(() => {
    if (horarios) {
      setFilas(horarios.map((h) => ({
        dia_semana: h.dia_semana,
        hora_inicio: horaCorta(h.hora_inicio),
        hora_fin: horaCorta(h.hora_fin),
        id_encargado: h.id_encargado ?? '',
      })));
    }
  }, [horarios]);

  if (cargando || !filas) return <Cargando />;

  const cambiar = (index, campo, valor) => {
    setError('');
    setFilas((actuales) => actuales.map((f, i) => (i === index ? { ...f, [campo]: valor } : f)));
  };

  const agregar = () => {
    const libres = DIAS.filter(([codigo]) => !filas.some((f) => f.dia_semana === codigo));
    if (libres.length === 0) {
      setError('Ya hay un horario para los 7 días de la semana.');
      return;
    }
    setError('');
    setFilas((actuales) => [...actuales, nuevaFila(libres[0][0])]);
  };

  const aplicarEjemplo = () => {
    setError('');
    setFilas([
      ...DIAS.slice(0, 5).map(([codigo]) => ({ ...nuevaFila(codigo), hora_inicio: '08:00', hora_fin: '20:00' })),
      { ...nuevaFila('SATURDAY'), hora_inicio: '08:00', hora_fin: '15:00' },
    ]);
  };

  const guardar = async () => {
    const diasVistos = new Set();
    for (const f of filas) {
      if (diasVistos.has(f.dia_semana)) {
        setError(`El día ${nombreDia(f.dia_semana)} está repetido.`);
        return;
      }
      diasVistos.add(f.dia_semana);
      if (!f.hora_inicio || !f.hora_fin || f.hora_fin <= f.hora_inicio) {
        setError(`En ${nombreDia(f.dia_semana)} la hora de fin debe ser mayor que la de inicio.`);
        return;
      }
    }
    setError('');
    const payload = filas.map((f) => ({
      dia_semana: f.dia_semana,
      hora_inicio: f.hora_inicio,
      hora_fin: f.hora_fin,
      id_encargado: f.id_encargado === '' ? null : Number(f.id_encargado),
    }));
    const r = await ejecutar(() => guardarHorariosCaja(payload), 'Horario de caja guardado');
    if (r) recargar();
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Configuración de caja</h1>
          <p>Horarios, encargados y cierre automático del cajón de la biblioteca.</p>
        </div>
      </div>

      <div className="info-box">
        Guarda un horario por día. Dentro del rango el sistema <b>abre la caja sola</b> con el
        encargado de ese día y al llegar la <b>hora de fin</b> la cierra. Si un día no tiene
        horario (o no tiene encargado), la caja de ese día no se abre ni se cierra sola.
        Los cambios <b>no afectan la caja que ya está abierta</b>: sigue con la hora con la
        que se abrió.
      </div>

      <div className="toolbar">
        <button className="btn-ghost btn-sm" onClick={aplicarEjemplo} disabled={enviando}>
          Ejemplo: Lun–Vie 8–20 y Sáb 8–15
        </button>
        <button className="btn-ghost btn-sm" onClick={agregar} disabled={enviando}>+ Agregar día</button>
      </div>

      <div className="card table-card">
        <table>
          <thead>
            <tr><th>Día</th><th>Encargado</th><th>Inicio</th><th>Fin (cierre automático)</th><th></th></tr>
          </thead>
          <tbody>
            {filas.length === 0 && (
              <tr><td colSpan={5} className="pick-sub">Sin horarios: la caja no se cierra sola.</td></tr>
            )}
            {filas.map((f, i) => (
              <tr key={i}>
                <td>
                  <select
                    value={f.dia_semana}
                    onChange={(e) => cambiar(i, 'dia_semana', e.target.value)}
                  >
                    {DIAS.filter(([codigo]) => !filas.some((otra, j) => j !== i && otra.dia_semana === codigo))
                      .map(([codigo, etiqueta]) => <option key={codigo} value={codigo}>{etiqueta}</option>)}
                  </select>
                </td>
                <td>
                  <select
                    value={f.id_encargado}
                    onChange={(e) => cambiar(i, 'id_encargado', e.target.value)}
                  >
                    <option value="">Sin asignar</option>
                    {(encargados || []).map((u) => (
                      <option key={u.id} value={u.id}>{u.nombre} {u.apellido}</option>
                    ))}
                  </select>
                </td>
                <td><input type="time" value={f.hora_inicio} onChange={(e) => cambiar(i, 'hora_inicio', e.target.value)} /></td>
                <td><input type="time" value={f.hora_fin} onChange={(e) => cambiar(i, 'hora_fin', e.target.value)} /></td>
                <td>
                  <button
                    className="btn-ghost btn-sm"
                    onClick={() => { setError(''); setFilas((actuales) => actuales.filter((_, j) => j !== i)); }}
                    disabled={enviando}
                  >
                    Quitar
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {error && <div className="error-form" style={{ marginTop: 12 }}>{error}</div>}

      <div className="acciones" style={{ marginTop: 16 }}>
        <button className="btn-primary" onClick={guardar} disabled={enviando}>
          {enviando ? 'Guardando...' : 'Guardar horarios'}
        </button>
      </div>

      <h3 className="seccion-titulo" style={{ marginTop: 26 }}>Vista previa</h3>
      <div className="card config-preview-caja">
        {filas.length === 0 ? (
          <p className="pick-sub">No hay horarios guardados.</p>
        ) : filas.map((f, i) => (
          <div className="caja-linea" key={i}>
            <span>{nombreDia(f.dia_semana)}</span>
            <b className="mono">{f.hora_inicio || '—'} – {f.hora_fin || '—'}</b>
          </div>
        ))}
      </div>
    </div>
  );
}

export default ConfiguracionCaja;
