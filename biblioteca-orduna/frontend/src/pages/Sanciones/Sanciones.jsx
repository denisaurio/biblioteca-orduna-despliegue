// Réplica del prototipo Figma "SANCIONES Y BLOQUEOS" (RF17): usuario, motivo, fecha inicio y fin,
// estado Activa / Finalizada. La sanción finaliza sola al cumplirse la fecha o al levantarla.
import { useState } from 'react';
import { getSanciones, levantarSancion } from '../../api/sancionesApi.js';
import { useApi, useAccion, useRefrescoTiempoReal } from '../../hooks.js';
import SearchInput from '../../components/common/SearchInput.jsx';
import Estado from '../../components/common/Estado.jsx';
import { FilaEstado } from '../../components/common/Cargando.jsx';
import FormSancion from './FormSancion.jsx';
import { estadoSancion } from '../../utils/estados.js';
import { codigo, fmtFecha } from '../../utils/formato.js';

function Sanciones() {
  const [soloActivas, setSoloActivas] = useState(false);
  const [busqueda, setBusqueda] = useState('');
  const [nueva, setNueva] = useState(false);
  const { datos: sanciones, cargando, recargar } = useApi(() => getSanciones(soloActivas), [soloActivas], []);
  useRefrescoTiempoReal(['sanciones', 'usuarios', 'prestamos'], recargar);
  const { ejecutar } = useAccion();

  const lista = sanciones.filter((s) => `${s.nombre_usuario} ${s.dni_usuario} ${s.motivo}`.toLowerCase().includes(busqueda.toLowerCase()));

  const levantar = async (s) => {
    if (!window.confirm(`¿Levantar la sanción de ${s.nombre_usuario}?`)) return;
    const ok = await ejecutar(() => levantarSancion(s.id), 'Sanción levantada');
    if (ok) recargar();
  };

  return (
    <div>
      <div className="page-head">
        <div><h1>Sanciones y bloqueos</h1><p>Suspensiones temporales de lectores (RF17).</p></div>
        <button className="btn-primary" onClick={() => setNueva(true)}>+ Aplicar sanción</button>
      </div>

      <div className="toolbar">
        <SearchInput placeholder="Buscar usuario o motivo..." value={busqueda} onChange={(e) => setBusqueda(e.target.value)} />
        <div className="tabs">
          <button className={!soloActivas ? 'active' : ''} onClick={() => setSoloActivas(false)}>Todas</button>
          <button className={soloActivas ? 'active' : ''} onClick={() => setSoloActivas(true)}>Activas</button>
        </div>
      </div>

      <div className="card table-card">
        <table>
          <thead><tr><th>Identificación</th><th>Usuario</th><th>Motivo</th><th>Fecha inicio</th><th>Fecha fin</th><th>Responsable</th><th>Estado</th><th></th></tr></thead>
          <tbody>
            {(cargando || lista.length === 0) && <FilaEstado columnas={8} cargando={cargando} vacio="No hay sanciones." />}
            {!cargando && lista.map((s) => (
              <tr key={s.id}>
                <td className="mono">{codigo('S', s.id)}</td>
                <td>{s.nombre_usuario}<div className="pick-sub">DNI {s.dni_usuario}</div></td>
                <td>{s.motivo}</td>
                <td className="mono">{fmtFecha(s.fecha_inicio)}</td>
                <td className="mono">{fmtFecha(s.fecha_fin)}</td>
                <td>{s.nombre_bibliotecario}</td>
                <td>
                  <Estado valor={s.estado} mapa={estadoSancion} />
                  {s.fecha_levantamiento && <div className="pick-sub">Levantada el {fmtFecha(s.fecha_levantamiento)}</div>}
                </td>
                <td>{s.estado === 'ACTIVA' && <button className="btn-link" onClick={() => levantar(s)}>Levantar</button>}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {nueva && <FormSancion onClose={() => setNueva(false)} onGuardado={() => { setNueva(false); recargar(); }} />}
    </div>
  );
}

export default Sanciones;
