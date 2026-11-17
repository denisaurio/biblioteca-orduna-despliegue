// "Reserva de libro físico" en el perfil del lector: cada préstamo activo con su día de inicio
// (y hora), su día de devolución y el detalle de la multa si se demora (tarifa copiada en el
// préstamo × días de retraso). Debajo, el detalle de las multas ya generadas.
import { getMisPrestamos, getMisMultas } from '../../api/miCuentaApi.js';
import { useApi, useRefrescoTiempoReal } from '../../hooks.js';
import Estado from '../../components/common/Estado.jsx';
import { Cargando, Vacio } from '../../components/common/Cargando.jsx';
import { estadoMulta, estadoPrestamo, nombreTipoPrestamo } from '../../utils/estados.js';
import { codigo, fmtFecha, fmtFechaHora, fmtSoles, hoyISO } from '../../utils/formato.js';
import './ReservasFisicas.css';

/** Días entre hoy y la fecha de devolución (negativo si ya pasó). */
function diasHasta(fechaISO) {
  if (!fechaISO) return null;
  const hoy = new Date(hoyISO() + 'T00:00:00');
  const fin = new Date(String(fechaISO).slice(0, 10) + 'T00:00:00');
  return Math.round((fin - hoy) / 86400000);
}

export function ReservasFisicas() {
  const { datos: prestamos, cargando, recargar: recargarPrestamos } = useApi(() => getMisPrestamos(), [], []);
  const { datos: multas, cargando: cargandoM, recargar: recargarMultas } = useApi(() => getMisMultas(), [], []);
  useRefrescoTiempoReal(['prestamos', 'devoluciones', 'multas'], () => {
    recargarPrestamos({ silencioso: true });
    recargarMultas({ silencioso: true });
  });

  const activos = prestamos.filter((p) => p.estado !== 'DEVUELTO');
  const deuda = multas.reduce((s, m) => s + Number(m.saldo), 0);

  return (
    <>
      <h3 className="seccion-titulo">Reserva de libro físico</h3>
      <p className="pick-sub" style={{ marginBottom: 12 }}>
        Los libros que tienes prestados ahora, cuándo los recogiste y hasta qué día puedes devolverlos.
      </p>
      {cargando ? <Cargando /> : activos.length === 0 ? <Vacio texto="No tienes libros físicos prestados." /> : (
        <div className="reservas-fisicas">
          {activos.map((p) => {
            const restantes = diasHasta(p.fecha_vencimiento);
            const tarifa = p.tarifa_multa_diaria != null ? Number(p.tarifa_multa_diaria) : null;
            return (
              <div key={p.id} className={'reserva-fisica' + (p.estado === 'VENCIDO' ? ' vencida' : '')}>
                <div className="reserva-cabecera">
                  <div>
                    <div className="pick-title">{p.titulo_libro}</div>
                    <div className="pick-sub">
                      {codigo('P', p.id)} · Ejemplar {p.codigo_inventario} · {nombreTipoPrestamo[p.tipo_prestamo] || p.tipo_prestamo}
                      {p.dias_prestamo ? ` (${p.dias_prestamo} día(s))` : ''}
                    </div>
                  </div>
                  <Estado valor={p.estado} mapa={estadoPrestamo} />
                </div>
                <dl className="detalle">
                  <dt>Día de inicio</dt><dd className="mono">{fmtFechaHora(p.fecha_prestamo)}</dd>
                  <dt>Día de devolución</dt>
                  <dd className="mono">
                    {p.fecha_vencimiento ? fmtFecha(p.fecha_vencimiento) : 'Sin fecha'}
                    {restantes != null && restantes >= 0 && (
                      <span className="pick-sub"> · {restantes === 0 ? 'vence hoy' : `quedan ${restantes} día(s)`}</span>
                    )}
                  </dd>
                  <dt>Multa por retraso</dt>
                  <dd className="mono">{tarifa != null ? `${fmtSoles(tarifa)} por cada día` : 'No genera multa'}</dd>
                </dl>
                {tarifa != null && (p.dias_retraso > 0 ? (
                  <div className="error-form" style={{ margin: '10px 0 0' }}>
                    Llevas <b>{p.dias_retraso} día(s)</b> de retraso: tu multa va en <b>{fmtSoles(p.multa_acumulada)}</b>
                    {' '}({p.dias_retraso} × {fmtSoles(tarifa)}) y sube {fmtSoles(tarifa)} cada día hasta que lo devuelvas.
                  </div>
                ) : (
                  <div className="info-box" style={{ marginTop: 10 }}>
                    Si te demoras pagarás {fmtSoles(tarifa)} por cada día de retraso:
                    1 día = {fmtSoles(tarifa)}, 3 días = {fmtSoles(tarifa * 3)}, 7 días = {fmtSoles(tarifa * 7)}.
                  </div>
                ))}
              </div>
            );
          })}
        </div>
      )}

      <h3 className="seccion-titulo" style={{ marginTop: 22 }}>Detalle de multas</h3>
      {deuda > 0 && (
        <p className="pick-sub" style={{ marginBottom: 10 }}>
          Debes <b>{fmtSoles(deuda)}</b>. Mientras tengas multas pendientes no podrás pedir préstamos nuevos.
        </p>
      )}
      {cargandoM ? <Cargando /> : multas.length === 0 ? <Vacio texto="No tienes multas. ¡Bien hecho!" /> : (
        <div className="table-card">
          <table>
            <thead><tr><th>Libro</th><th>Motivo</th><th>Fecha</th><th>Monto</th><th>Pagado</th><th>Saldo</th><th>Estado</th></tr></thead>
            <tbody>
              {multas.map((m) => (
                <tr key={m.id}>
                  <td>{m.titulo_libro}</td>
                  <td>{m.concepto || (m.dias_retraso > 0 ? `Retraso (${m.dias_retraso} día(s))` : 'Multa')}</td>
                  <td className="mono">{fmtFecha(m.fecha_generacion)}</td>
                  <td className="mono">{fmtSoles(m.monto)}</td>
                  <td className="mono">{fmtSoles(m.monto_pagado)}</td>
                  <td className="mono">{fmtSoles(m.saldo)}</td>
                  <td><Estado valor={m.estado} mapa={estadoMulta} /></td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </>
  );
}

export default ReservasFisicas;
