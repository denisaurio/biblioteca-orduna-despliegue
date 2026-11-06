// Auditoría: quién hizo qué en la biblioteca. Solo la ve el gerente.
//
// Cada acción del personal queda registrada con su autor, su fecha y una frase legible
// (registró un libro, escaneó un ISBN, cobró una multa, abrió la caja...). La lista se
// filtra por fechas, por persona y por tipo de acción.
import { useState } from 'react';
import { getAuditoria } from '../../api/auditoriaApi.js';
import { getUsuarios } from '../../api/usuariosApi.js';
import { useApi, useRefrescoTiempoReal } from '../../hooks.js';
import Estado from '../../components/common/Estado.jsx';
import Paginacion from '../../components/common/Paginacion.jsx';
import { FilaEstado } from '../../components/common/Cargando.jsx';
import { entidadAuditoria, tipoAccionAuditoria } from '../../utils/estados.js';
import { fmtFechaHora, hoyISO, inicioMesISO, nombreRol } from '../../utils/formato.js';
import './Auditoria.css';

const TAMANIO = 15;

function Auditoria() {
  const [desde, setDesde] = useState(inicioMesISO());
  const [hasta, setHasta] = useState(hoyISO());
  const [idUsuario, setIdUsuario] = useState('');
  const [accion, setAccion] = useState('');
  const [pagina, setPagina] = useState(0);

  const { datos, cargando, recargar } = useApi(
    () => getAuditoria({
      desde: desde || undefined,
      hasta: hasta || undefined,
      id_usuario: idUsuario || undefined,
      accion: accion || undefined,
      pagina,
      tamanio: TAMANIO,
    }),
    [desde, hasta, idUsuario, accion, pagina]
  );
  useRefrescoTiempoReal(['*'], recargar);

  const lista = datos?.contenido || [];
  const acciones = Object.keys(tipoAccionAuditoria);

  // El personal que puede haber hecho algo (los lectores no se auditan, salvo su registro).
  const { datos: usuarios } = useApi(() => getUsuarios({ pagina: 0, tamanio: 100 }), [], null);
  const personal = (usuarios?.contenido || []).filter((u) => u.rol !== 'LECTOR');

  const cambiar = (setter) => (valor) => {
    setter(valor);
    setPagina(0);
  };

  return (
    <div>
      <div className="page-head">
        <div>
          <h1>Auditoria</h1>
          <p>Que hizo cada persona del personal, con fecha y hora.</p>
        </div>
      </div>

      <div className="card aud-filtros">
        <div className="filtros-row">
          <div>
            <label>Desde</label>
            <input type="date" value={desde} onChange={(e) => cambiar(setDesde)(e.target.value)} />
          </div>
          <div>
            <label>Hasta</label>
            <input type="date" value={hasta} onChange={(e) => cambiar(setHasta)(e.target.value)} />
          </div>
          <div>
            <label>Persona</label>
            <select className="select-inline" value={idUsuario} onChange={(e) => cambiar(setIdUsuario)(e.target.value)}>
              <option value="">Todo el personal</option>
              {personal.map((u) => (
                <option key={u.id} value={u.id}>{u.nombre} {u.apellido}</option>
              ))}
            </select>
          </div>
          <div>
            <label>Accion</label>
            <select className="select-inline" value={accion} onChange={(e) => cambiar(setAccion)(e.target.value)}>
              <option value="">Todas las acciones</option>
              {acciones.map((a) => (
                <option key={a} value={a}>{tipoAccionAuditoria[a][1]}</option>
              ))}
            </select>
          </div>
        </div>
      </div>

      <div className="card table-card">
        <table>
          <thead>
            <tr><th>Fecha</th><th>Persona</th><th>Accion</th><th>Sobre</th><th>Detalle</th></tr>
          </thead>
          <tbody>
            {(cargando || lista.length === 0) && (
              <FilaEstado columnas={5} cargando={cargando} vacio="No hay acciones registradas en este rango." />
            )}
            {!cargando && lista.map((a) => (
              <tr key={a.id}>
                <td className="mono">{fmtFechaHora(a.fecha)}</td>
                <td>
                  {a.nombre_usuario || 'Sin sesion'}
                  {a.rol_usuario && <div className="pick-sub">{nombreRol[a.rol_usuario]}</div>}
                </td>
                <td><Estado valor={a.accion} mapa={tipoAccionAuditoria} /></td>
                <td>
                  {entidadAuditoria[a.entidad] || a.entidad}
                  {a.entidad_id != null && <div className="pick-sub mono">#{a.entidad_id}</div>}
                </td>
                <td className="aud-detalle">{a.descripcion}</td>
              </tr>
            ))}
          </tbody>
        </table>
        {datos && (
          <Paginacion pagina={datos.pagina} totalPaginas={datos.total_paginas} totalElementos={datos.total_elementos}
                      tamanio={datos.tamanio} onCambiar={setPagina} />
        )}
      </div>
    </div>
  );
}

export default Auditoria;
