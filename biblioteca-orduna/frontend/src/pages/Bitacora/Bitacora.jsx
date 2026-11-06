// Réplica del prototipo Figma "BITACORA DE TURNOS" (RF16): filtros Desde / Hasta / Turno y tabla.
// Cada bibliotecario abre y cierra SU turno (solo uno abierto a la vez). El gerente ve a todo el personal.
import { useState } from 'react';
import { useAuth } from '../../context/AuthContext.jsx';
import { getTurnos, getTurnoActual, abrirTurno, cerrarTurno } from '../../api/bitacoraApi.js';
import { useApi, useAccion, useRefrescoTiempoReal } from '../../hooks.js';
import Estado from '../../components/common/Estado.jsx';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import { FilaEstado } from '../../components/common/Cargando.jsx';
import { codigo, fmtFecha, fmtHora, inicioMesISO, hoyISO, nombreRol } from '../../utils/formato.js';

function Bitacora() {
  const { esGerente } = useAuth();
  const [desde, setDesde] = useState(inicioMesISO());
  const [hasta, setHasta] = useState(hoyISO());
  const [tipo, setTipo] = useState('');
  const [cerrar, setCerrar] = useState(false);
  const { datos: turnos, cargando, recargar } = useApi(() => getTurnos({ desde, hasta, tipo: tipo || undefined }), [desde, hasta, tipo], []);
  const { datos: actual, recargar: recargarActual } = useApi(() => getTurnoActual(), [], null);
  useRefrescoTiempoReal(['turnos'], () => {
    recargar({ silencioso: true });
    recargarActual({ silencioso: true });
  });
  const { ejecutar, enviando } = useAccion();

  const abrir = async () => {
    const r = await ejecutar(() => abrirTurno(), 'Turno abierto');
    if (r) { recargar(); recargarActual(); }
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Bitácora de turnos</h1>
          <p>{actual ? `Tienes un turno abierto desde las ${fmtHora(actual.fecha_apertura)} (${fmtFecha(actual.fecha_apertura)}).` : 'No tienes un turno abierto.'}</p>
        </div>
        {actual
          ? <button className="btn-primary" onClick={() => setCerrar(true)}>Cerrar mi turno</button>
          : <button className="btn-primary" onClick={abrir} disabled={enviando}>+ Abrir turno</button>}
      </div>

      <div className="card" style={{ padding: 16, marginBottom: 16 }}>
        <div className="filtros-row">
          <div><label>Desde</label><input type="date" value={desde} onChange={(e) => setDesde(e.target.value)} /></div>
          <div><label>Hasta</label><input type="date" value={hasta} onChange={(e) => setHasta(e.target.value)} /></div>
          {esGerente && (
            <div>
              <label>Turno</label>
              <select value={tipo} onChange={(e) => setTipo(e.target.value)}>
                <option value="">Todos</option>
                <option value="GERENTE">Gerente</option>
                <option value="BIBLIOTECARIO">Bibliotecario</option>
              </select>
            </div>
          )}
        </div>
      </div>

      <div className="card table-card">
        <table>
          <thead><tr><th>Identificación</th><th>Usuario</th><th>Tipo</th><th>Fecha</th><th>Apertura</th><th>Cierre</th><th>Observaciones</th><th>Estado</th></tr></thead>
          <tbody>
            {(cargando || turnos.length === 0) && <FilaEstado columnas={8} cargando={cargando} vacio="No hay turnos en este rango." />}
            {!cargando && turnos.map((t) => (
              <tr key={t.id}>
                <td className="mono">{codigo('T', t.id)}</td>
                <td>{t.nombre_bibliotecario}</td>
                <td>{nombreRol[t.tipo]}</td>
                <td className="mono">{fmtFecha(t.fecha_apertura)}</td>
                <td className="mono">{fmtHora(t.fecha_apertura)}</td>
                <td className="mono">{t.fecha_cierre ? fmtHora(t.fecha_cierre) : '—'}</td>
                <td>{t.observaciones || <span className="pick-sub">Sin novedades</span>}</td>
                <td><Estado valor={t.estado} mapa={{ ABIERTO: ['amber', 'Abierto'], CERRADO: ['green', 'Cerrado'] }} /></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {cerrar && <CerrarTurno onClose={() => setCerrar(false)} onHecho={() => { setCerrar(false); recargar(); recargarActual(); }} />}
    </div>
  );
}

function CerrarTurno({ onClose, onHecho }) {
  const [obs, setObs] = useState('');
  const { ejecutar, enviando } = useAccion();
  const enviar = async (e) => {
    e.preventDefault();
    const r = await ejecutar(() => cerrarTurno(obs), 'Turno cerrado');
    if (r) onHecho();
  };
  return (
    <Modal titulo="Cerrar turno" subtitulo="Registre las incidencias del turno (opcional)." onClose={onClose}
           pie={<><button className="btn-ghost" onClick={onClose}>Cancelar</button><button className="btn-primary" form="form-turno" disabled={enviando}>Cerrar turno</button></>}>
      <form id="form-turno" onSubmit={enviar}>
        <Campo label="Observaciones"><textarea value={obs} onChange={(e) => setObs(e.target.value)} maxLength={500} placeholder="Sin novedades" /></Campo>
      </form>
    </Modal>
  );
}

export default Bitacora;
