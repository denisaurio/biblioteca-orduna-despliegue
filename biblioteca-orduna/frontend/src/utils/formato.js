// Formatos para mostrar datos del backend (fechas ISO, montos en soles, etc.)

const pad = (n) => String(n).padStart(2, '0');

/** "2026-10-01" o "2026-10-01T18:30:00" -> "01/10/2026" */
export function fmtFecha(valor) {
  if (!valor) return '—';
  const [y, m, d] = String(valor).slice(0, 10).split('-');
  return `${d}/${m}/${y}`;
}

/** "2026-10-01T18:30:00" -> "01/10/2026 18:30" */
export function fmtFechaHora(valor) {
  if (!valor) return '—';
  const s = String(valor);
  return `${fmtFecha(s)} ${s.slice(11, 16)}`;
}

/** "2026-10-01T18:30:00" -> "18:30" */
export function fmtHora(valor) {
  if (!valor) return '—';
  return String(valor).slice(11, 16);
}

/** 3.5 -> "S/ 3.50" */
export function fmtSoles(valor) {
  const n = Number(valor ?? 0);
  return `S/ ${n.toFixed(2)}`;
}

/** Fecha de hoy en formato del <input type="date"> */
export function hoyISO(sumarDias = 0) {
  const d = new Date();
  d.setDate(d.getDate() + sumarDias);
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}`;
}

/** Primer día del mes actual (yyyy-mm-dd) */
export function inicioMesISO() {
  const d = new Date();
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-01`;
}

/** "Ana Pérez" -> "AP" */
export function iniciales(nombre = '') {
  const partes = nombre.trim().split(/\s+/).filter(Boolean);
  return ((partes[0]?.[0] || '') + (partes[1]?.[0] || '')).toUpperCase() || '?';
}

/** Código visible: 7 -> "U007" */
export function codigo(prefijo, id) {
  return `${prefijo}${String(id ?? '').padStart(3, '0')}`;
}

export const nombreRol = { LECTOR: 'Lector', BIBLIOTECARIO: 'Bibliotecario', GERENTE: 'Gerente', INVITADO: 'Invitado' };

export const nombreTipoUsuario = { ESTUDIANTE: 'Estudiante', DOCENTE: 'Docente', EXTERNO: 'Externo' };

/** Color de portada estable a partir del id (mismo estilo que el prototipo). */
export function colorPortada(seed = 0) {
  const paleta = [
    'linear-gradient(145deg,#6B8E4E,#3F5E2E)',
    'linear-gradient(145deg,#3E6FA6,#1C3F63)',
    'linear-gradient(145deg,#A6453E,#5E201C)',
    'linear-gradient(145deg,#B5873F,#7A5420)',
    'linear-gradient(145deg,#5E4E8E,#2F2650)',
    'linear-gradient(145deg,#2E7D7A,#174845)',
  ];
  return paleta[Number(seed) % paleta.length];
}
