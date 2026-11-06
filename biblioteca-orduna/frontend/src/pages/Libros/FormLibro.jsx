// Registrar / editar libro (RF01). El formulario registra la OBRA ORIGINAL:
// escaner de ISBN (lector fisico en la PC o camara en el celular), tipo de libro
// (fisico / virtual / hibrido), precios, portada y PDF.
//
// Al escanear (o escribir) el ISBN se rellena TODO solo: titulo, autores, editorial,
// año, paginas, generos y la portada. Los autores y generos que la biblioteca ya tiene
// se marcan; los que no, se marcan como "nuevos" y el backend los crea al guardar.
//
// Las COPIAS (ejemplares) NO se dan de alta aqui: se crean en el modulo Ejemplares,
// que es donde se lleva el control de las copias fisicas que tiene el negocio.
import { useEffect, useMemo, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  actualizarLibroCompleto,
  buscarPorIsbn,
  crearGenero,
  crearLibroCompleto,
  getGeneros,
} from '../../api/librosApi.js';
import { getAutores } from '../../api/autoresApi.js';
import { getEjemplares } from '../../api/ejemplaresApi.js';
import { mensajeError, urlArchivo } from '../../api/axiosClient.js';
import { useToast } from '../../context/ToastContext.jsx';
import { useApi, useAccion } from '../../hooks.js';
import Modal from '../../components/common/Modal.jsx';
import Campo from '../../components/common/Campo.jsx';
import EscanerCodigo from '../../components/common/EscanerCodigo.jsx';

const TIPOS = [
  { valor: 'FISICO', texto: 'Físico', ayuda: 'Se presta en el mostrador. Sus copias se dan de alta en Ejemplares.' },
  { valor: 'VIRTUAL', texto: 'Virtual', ayuda: 'Solo PDF: se alquila o se compra en línea.' },
  { valor: 'HIBRIDO', texto: 'Híbrido', ayuda: 'Copias en la biblioteca y PDF a la vez. Las copias van en Ejemplares.' },
];

const FUENTES = {
  GOOGLE_BOOKS: 'Google Books',
  OPEN_LIBRARY: 'Open Library',
  'GOOGLE_BOOKS+OPEN_LIBRARY': 'Google Books + Open Library',
  BIBLIOTECA: 'la propia biblioteca',
};

const soloDigitos = (texto) => (texto || '').replace(/[\s-]/g, '').toUpperCase();
const esIsbn = (isbn) => /^\d{9}[\dX]$|^\d{13}$/.test(isbn);
/** Digito de control del ISBN: si no cuadra, hay un numero cambiado, de mas o de menos. */
const controlValido = (isbn) => {
  if (/^\d{13}$/.test(isbn)) {
    const suma = [...isbn].reduce((t, c, i) => t + Number(c) * (i % 2 === 0 ? 1 : 3), 0);
    return suma % 10 === 0;
  }
  if (/^\d{9}[\dX]$/.test(isbn)) {
    const suma = [...isbn].reduce((t, c, i) => t + (c === 'X' ? 10 : Number(c)) * (10 - i), 0);
    return suma % 11 === 0;
  }
  return false;
};
/** Compara nombres sin tildes ni mayúsculas ("García" == "garcia"). */
const clave = (t) => (t || '').normalize('NFD').replace(/\p{M}/gu, '').replace(/[^\p{L}\p{N}]+/gu, ' ').trim().toLowerCase();
const unir = (a, b) => [...new Set([...a, ...b])];

function FormLibro({ libro, onClose, onGuardado }) {
  const navegar = useNavigate();
  const toast = useToast();
  const isbnRef = useRef(null);
  const ultimoBuscado = useRef('');
  const enCurso = useRef(false);
  const [f, setF] = useState({
    titulo: libro?.titulo || '',
    isbn: libro?.isbn || '',
    editorial: libro?.editorial || '',
    anio_publicacion: libro?.anio_publicacion || '',
    num_paginas: libro?.num_paginas || '',
    ids_autores: libro?.autores.map((a) => a.id) || [],
    ids_generos: libro?.generos.map((g) => g.id) || [],
  });
  const [tipo, setTipo] = useState(libro?.tipo_libro || 'FISICO');
  const [precioAlquiler, setPrecioAlquiler] = useState(libro?.precio_alquiler_diario ?? '');
  const [precioCompra, setPrecioCompra] = useState(libro?.precio_compra ?? '');
  const [portada, setPortada] = useState(null);
  const [urlPortada, setUrlPortada] = useState(libro?.url_imagen || '');
  const [quitarPortada, setQuitarPortada] = useState(false);
  const [pdf, setPdf] = useState(null);
  const [nuevoGenero, setNuevoGenero] = useState('');
  const [nuevoAutor, setNuevoAutor] = useState('');
  // Autores y generos que trajo el escaner y que la biblioteca todavia no tiene.
  const [autoresNuevos, setAutoresNuevos] = useState([]);
  const [generosNuevos, setGenerosNuevos] = useState([]);
  const [existente, setExistente] = useState(null);
  const [fuente, setFuente] = useState('');
  const [error, setError] = useState('');
  const [buscando, setBuscando] = useState(false);

  const { datos: autores, recargar: recargarAutores } = useApi(() => getAutores(), [], []);
  const { datos: generos, recargar: recargarGeneros } = useApi(() => getGeneros(), [], []);
  const { datos: copiasExistentes } = useApi(
    () => (libro ? getEjemplares({ id_libro: libro.id }) : Promise.resolve({ status: 200, data: [] })),
    [libro?.id],
    []
  );
  const { ejecutar, enviando } = useAccion();

  // Libro nuevo: el cursor queda en el ISBN, listo para pasar el lector de codigos.
  useEffect(() => {
    if (!libro) isbnRef.current?.focus();
  }, [libro]);

  // Vista previa del archivo elegido (y se libera la memoria al cambiarlo).
  const vistaArchivo = useMemo(() => (portada ? URL.createObjectURL(portada) : ''), [portada]);
  useEffect(() => () => { if (vistaArchivo) URL.revokeObjectURL(vistaArchivo); }, [vistaArchivo]);
  const vistaPortada = vistaArchivo || (quitarPortada ? '' : urlArchivo(urlPortada));
  // Si la imagen no carga (sin internet, catalogo caido), se muestra el titulo en su lugar.
  const [falloVista, setFalloVista] = useState(false);
  useEffect(() => setFalloVista(false), [vistaPortada]);

  const set = (k) => (e) => setF({ ...f, [k]: e.target.value });
  const alternar = (k, id) => setF({ ...f, [k]: f[k].includes(id) ? f[k].filter((x) => x !== id) : [...f[k], id] });

  /**
   * Escanea el ISBN: el backend busca los datos y la portada; aqui se rellena el formulario.
   * codigo: llega del lector fisico o de la camara; si no, se usa lo escrito en el campo.
   */
  const escanear = async (codigo) => {
    setError('');
    const isbn = soloDigitos(codigo ?? f.isbn);
    if (!esIsbn(isbn)) {
      setError(`El ISBN debe tener 10 o 13 dígitos y este tiene ${isbn.length}. Revisa que no sobre ni falte un número (el ISBN es el número que está debajo del código de barras, empieza con 978 o 979).`);
      return;
    }
    if (!controlValido(isbn)) {
      setError(`El ISBN ${isbn} no es válido: el último dígito no cuadra. Seguramente hay un número cambiado o repetido. Pasa el lector de códigos o revisa el número impreso.`);
      return;
    }
    // El lector escribe el 13er digito (busqueda automatica) y enseguida pulsa Enter:
    // no se busca dos veces el mismo ISBN.
    if (enCurso.current) return;
    enCurso.current = true;
    ultimoBuscado.current = isbn;
    setF((x) => ({ ...x, isbn }));
    setBuscando(true);
    let datos;
    try {
      const res = await buscarPorIsbn(isbn);
      datos = res.data;
    } catch (e) {
      const mensaje = e.response?.data?.mensaje;
      // Un 404 CON mensaje es del negocio: el ISBN no esta en Google Books ni en Open
      // Library. Un 404 SIN mensaje (o con el de "ruta inexistente") es que el backend
      // va viejo y no tiene la ruta del escaner.
      if (e.response?.status === 404 && (!mensaje || mensaje === 'La ruta no existe')) {
        toast.error('El backend no tiene la ruta del escáner: reinícialo (Ctrl+C y "mvn spring-boot:run") para que cargue la versión nueva.');
      } else {
        toast.error(mensajeError(e));
      }
      return;
    } finally {
      enCurso.current = false;
      setBuscando(false);
    }
    if (!datos) return;
    aplicarEscaneo(datos);
  };

  /** Rellena el formulario con lo que encontro el escaner. */
  const aplicarEscaneo = (datos) => {
    // Libro nuevo: manda lo escaneado. Edicion: solo se rellenan los campos vacios.
    const elegir = (actual, escaneado) => (libro ? actual || escaneado || '' : escaneado || actual || '');
    const idsAutores = (datos.autores_existentes || []).map((a) => a.id);
    const idsGeneros = (datos.generos_existentes || []).map((g) => g.id);
    setF((x) => ({
      ...x,
      isbn: datos.isbn || x.isbn,
      titulo: elegir(x.titulo, datos.titulo),
      editorial: elegir(x.editorial, datos.editorial),
      anio_publicacion: elegir(x.anio_publicacion, datos.anio_publicacion),
      num_paginas: elegir(x.num_paginas, datos.num_paginas),
      ids_autores: libro ? unir(x.ids_autores, idsAutores) : idsAutores.length ? idsAutores : x.ids_autores,
      ids_generos: libro ? unir(x.ids_generos, idsGeneros) : idsGeneros.length ? idsGeneros : x.ids_generos,
    }));
    setAutoresNuevos(datos.autores_nuevos || []);
    setGenerosNuevos(datos.generos_nuevos || []);
    setFuente(datos.fuente || '');
    // La portada de internet se usa si no se eligio un archivo propio.
    if (datos.url_portada && datos.fuente !== 'BIBLIOTECA') {
      setUrlPortada(datos.url_portada);
      setQuitarPortada(false);
    }
    // El ISBN ya esta registrado (y no es el libro que se esta editando).
    if (datos.id_libro_existente && datos.id_libro_existente !== libro?.id) {
      setExistente({ id: datos.id_libro_existente, titulo: datos.titulo_libro_existente });
    } else {
      setExistente(null);
    }
    // Recarga las listas: otra pestaña pudo haber creado autores o generos.
    recargarAutores();
    recargarGeneros();
  };

  /** El lector de codigos escribe rapido: con 13 digitos (978/979) se busca solo. */
  const cambiarIsbn = (e) => {
    const valor = e.target.value;
    setF((x) => ({ ...x, isbn: valor }));
    const limpio = soloDigitos(valor);
    if (/^97[89]\d{10}$/.test(limpio) && controlValido(limpio) && limpio !== ultimoBuscado.current) {
      escanear(limpio);
    }
  };

  const agregarGenero = async () => {
    const nombre = nuevoGenero.trim();
    if (!nombre) return;
    const ya = generos.find((g) => clave(g.nombre) === clave(nombre));
    if (ya) {
      setF((x) => ({ ...x, ids_generos: unir(x.ids_generos, [ya.id]) }));
      setNuevoGenero('');
      return;
    }
    const g = await ejecutar(() => crearGenero(nombre), 'Género creado');
    if (g) {
      setNuevoGenero('');
      await recargarGeneros();
      setF((x) => ({ ...x, ids_generos: [...x.ids_generos, g.id] }));
    }
  };

  /** Autor que no esta en la lista: se crea al guardar el libro (no hace falta ir a Autores). */
  const agregarAutor = () => {
    const nombre = nuevoAutor.replace(/\s+/g, ' ').trim();
    if (!nombre) return;
    const ya = autores.find((a) => clave(a.nombre) === clave(nombre));
    if (ya) {
      setF((x) => ({ ...x, ids_autores: unir(x.ids_autores, [ya.id]) }));
    } else if (!autoresNuevos.some((n) => clave(n) === clave(nombre))) {
      setAutoresNuevos((l) => [...l, nombre]);
    }
    setNuevoAutor('');
  };

  const quitarDeLista = (setLista, nombre) => setLista((l) => l.filter((n) => n !== nombre));

  const guardar = async (e) => {
    e.preventDefault();
    setError('');

    if (tipo !== 'FISICO' && !pdf && !libro?.tiene_pdf) {
      setError('Un libro virtual necesita su PDF: súbelo antes de guardar.');
      return;
    }
    if (f.ids_autores.length + autoresNuevos.length === 0) {
      setError('Indica al menos un autor (elige uno de la lista o escribe su nombre).');
      return;
    }
    if (f.ids_generos.length + generosNuevos.length === 0) {
      setError('Indica al menos un género.');
      return;
    }

    const datos = {
      titulo: f.titulo.trim(),
      isbn: soloDigitos(f.isbn),
      editorial: String(f.editorial || '').trim() || null,
      anio_publicacion: f.anio_publicacion ? Number(f.anio_publicacion) : null,
      num_paginas: f.num_paginas ? Number(f.num_paginas) : null,
      ids_autores: f.ids_autores,
      ids_generos: f.ids_generos,
      autores_nuevos: autoresNuevos,
      generos_nuevos: generosNuevos,
      tipo_libro: tipo,
      precio_alquiler_diario: tipo === 'FISICO' ? null : Number(precioAlquiler || 0),
      precio_compra: precioCompra === '' ? null : Number(precioCompra),
      // Las copias no se crean aqui: se dan de alta en el modulo Ejemplares.
      ejemplares: [],
      url_portada: quitarPortada ? null : urlPortada || null,
      quitar_portada: quitarPortada,
    };

    const guardado = await ejecutar(
      () => (libro ? actualizarLibroCompleto(libro.id, datos, portada, pdf) : crearLibroCompleto(datos, portada, pdf)),
      libro ? 'Libro actualizado' : 'Libro registrado'
    );
    if (guardado) {
      if (guardado.aviso) toast.aviso(guardado.aviso);
      onGuardado();
    }
  };

  const virtual = tipo !== 'FISICO';
  const hayPortada = !!vistaPortada;
  const verImagen = hayPortada && !falloVista;

  return (
    <Modal
      titulo={libro ? 'Editar libro' : 'Registrar libro'}
      subtitulo="Escanea el ISBN: los datos y la portada se rellenan solos"
      onClose={onClose}
      ancho={760}
      pie={
        <>
          <button className="btn-ghost" onClick={onClose}>Cancelar</button>
          <button className="btn-primary" form="form-libro" disabled={enviando || buscando}>
            {enviando ? 'Guardando...' : libro ? 'Guardar cambios' : 'Registrar libro'}
          </button>
        </>
      }
    >
      <form id="form-libro" className="form-grid" onSubmit={guardar}>
        {error && <div className="error-form" style={{ gridColumn: 'span 2' }}>{error}</div>}

        {/* ---- Escaner de codigo de barras ---- */}
        <Campo
          label="Código de barras (ISBN)"
          ancho={2}
          ayuda="Escanea el código del libro: se rellenan el título, los autores, la editorial, el año, las páginas, los géneros y la portada."
        >
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap' }}>
            <input
              ref={isbnRef}
              value={f.isbn}
              onChange={cambiarIsbn}
              placeholder="978-0-306-40615-7"
              inputMode="numeric"
              autoComplete="off"
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); escanear(); } }}
            />
            <button type="button" className="btn-ghost" onClick={() => escanear()} disabled={buscando}>
              {buscando ? 'Buscando...' : 'Buscar'}
            </button>
            <EscanerCodigo formatos={['ean_13', 'ean_8', 'upc_a']} onCodigo={(codigo) => escanear(codigo)} />
          </div>
          <small>
            En la PC: pasa el lector de códigos (escribe el número y pulsa Enter). En el celular: usa la cámara.
            {fuente && <> · Datos tomados de <b>{FUENTES[fuente] || fuente}</b>.</>}
          </small>
        </Campo>

        {existente && (
          <div className="error-form" style={{ gridColumn: 'span 2', display: 'flex', gap: 10, alignItems: 'center', justifyContent: 'space-between' }}>
            <span>Este ISBN ya está registrado: <b>{existente.titulo}</b>. Si llegó otra copia, agrégala como ejemplar.</span>
            <button type="button" className="btn-ghost btn-sm" onClick={() => { onClose(); navegar('/ejemplares?libro=' + existente.id); }}>
              Agregar copias
            </button>
          </div>
        )}

        {/* ---- Portada (opcional) ---- */}
        <Campo label="Portada (opcional)" ancho={2} ayuda="Se usa la portada hallada por el ISBN si no subes un archivo. Se guarda en Cloudflare R2.">
          <div style={{ display: 'flex', gap: 14, alignItems: 'flex-start' }}>
            <div className="portada form-portada">
              {verImagen ? (
                <img
                  src={vistaPortada}
                  alt="Portada"
                  onError={() => setFalloVista(true)}
                  style={{ width: '100%', height: '100%', objectFit: 'cover' }}
                />
              ) : (
                <span className="portada-titulo" style={{ fontSize: 11 }}>{f.titulo || 'Sin portada'}</span>
              )}
            </div>
            <div style={{ flex: 1, display: 'flex', flexDirection: 'column', gap: 6 }}>
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={(e) => { setPortada(e.target.files?.[0] || null); setQuitarPortada(false); }}
              />
              {portada && <small>Archivo nuevo: se reemplaza al guardar.</small>}
              {!portada && urlPortada && !quitarPortada && /^https?:\/\/(books\.google|covers\.)/.test(urlPortada) && (
                <small>Portada encontrada por el ISBN: se guardará en R2 al registrar.</small>
              )}
              {hayPortada && (
                <button
                  type="button"
                  className="btn-ghost btn-sm"
                  style={{ alignSelf: 'flex-start' }}
                  onClick={() => { setPortada(null); setQuitarPortada(true); }}
                >
                  Quitar portada
                </button>
              )}
            </div>
          </div>
        </Campo>

        {/* ---- Datos del libro ---- */}
        <Campo label="Título" ancho={2}><input value={f.titulo} onChange={set('titulo')} required maxLength={200} /></Campo>
        <Campo label="Editorial"><input value={f.editorial} onChange={set('editorial')} maxLength={120} /></Campo>
        <Campo label="Año de publicación"><input type="number" value={f.anio_publicacion} onChange={set('anio_publicacion')} min="1000" max="2100" /></Campo>
        <Campo label="Número de páginas"><input type="number" value={f.num_paginas} onChange={set('num_paginas')} min="1" /></Campo>

        {/* ---- Tipo de libro ---- */}
        <Campo label="Tipo de libro" ancho={2}>
          <div className="chips">
            {TIPOS.map((t) => (
              <span key={t.valor} className={'chip' + (tipo === t.valor ? ' on' : '')} onClick={() => setTipo(t.valor)} title={t.ayuda}>
                {t.texto}
              </span>
            ))}
          </div>
          <small>{TIPOS.find((t) => t.valor === tipo)?.ayuda}</small>
        </Campo>

        {/* ---- Precios ---- */}
        {virtual ? (
          <>
            <Campo label="Precio de alquiler por día (S/)" ayuda="Lo que cuesta leerlo un día. Ejemplo: 1.00">
              <input type="number" step="0.01" min="0" value={precioAlquiler} onChange={(e) => setPrecioAlquiler(e.target.value)} required />
            </Campo>
            <Campo label="Precio de compra (S/)" ayuda="Lo que cuesta el PDF en propiedad (permite descargarlo).">
              <input type="number" step="0.01" min="0" value={precioCompra} onChange={(e) => setPrecioCompra(e.target.value)} required />
            </Campo>
            <Campo label="PDF del libro" ancho={2} ayuda="Archivo PDF (máximo 50 MB). Se guarda en el bucket privado de R2.">
              <input type="file" accept="application/pdf" onChange={(e) => setPdf(e.target.files?.[0] || null)} />
              {libro?.tiene_pdf && !pdf && <small>El libro ya tiene un PDF guardado. Sube otro solo si quieres reemplazarlo.</small>}
            </Campo>
          </>
        ) : (
          <Campo label="Precio general del ejemplar (S/)" ancho={2} ayuda="Lo que cuesta el ejemplar en la tienda. Cada copia puede tener el suyo.">
            <input type="number" step="0.01" min="0" value={precioCompra} onChange={(e) => setPrecioCompra(e.target.value)} required />
          </Campo>
        )}

        {/* ---- Copias (ejemplares): se dan de alta en el modulo Ejemplares ---- */}
        {libro ? (
          virtual ? null : (
          <div style={{ gridColumn: 'span 2' }}>
            <div className="summary-row" style={{ borderBottom: 'none', paddingBottom: 4 }}>
              <strong style={{ fontSize: 12.5 }}>Copias de este libro ({copiasExistentes.length})</strong>
              <button type="button" className="btn-ghost btn-sm" onClick={() => { onClose(); navegar('/ejemplares?libro=' + libro.id); }}>
                Agregar o editar copias
              </button>
            </div>
            {copiasExistentes.length === 0 ? (
              <div className="info-box">
                Este libro todavía no tiene copias: sin ejemplares no se puede prestar.
                Las copias se dan de alta en el módulo Ejemplares ("Agregar o editar copias"), con su código de
                inventario y su ubicación.
              </div>
            ) : (
              <div className="card" style={{ padding: 12, display: 'flex', flexDirection: 'column', gap: 8 }}>
                {copiasExistentes.map((c) => (
                  <div key={c.id} style={{ display: 'flex', gap: 10, alignItems: 'center', fontSize: 13 }}>
                    <span className="mono" style={{ flex: 1 }}>{c.codigo_inventario}</span>
                    <span>{c.ubicacion || 'Sin ubicación'}</span>
                    <span className="mono">S/ {c.precio ?? c.precio_libro ?? '—'}</span>
                  </div>
                ))}
              </div>
            )}
            <small>
              El precio de cada copia se copia al préstamo cuando se presta (igual que la tarifa de multa).
            </small>
          </div>
          )
        ) : virtual ? null : (
          <div className="info-box" style={{ gridColumn: 'span 2' }}>
            Este formulario registra la obra original. Las copias (ejemplares) que tiene la biblioteca se dan de
            alta en el módulo <strong>Ejemplares</strong>, cada una con su código de inventario, su ubicación y
            su precio. Se podrán añadir después de guardar el libro.
          </div>
        )}

        {/* ---- Autores ---- */}
        <Campo
          label={`Autores (${f.ids_autores.length + autoresNuevos.length} seleccionados)`}
          ancho={2}
          ayuda={autoresNuevos.length ? 'Los marcados con ＋ son nuevos: se registran solos al guardar el libro.' : undefined}
        >
          <div className="chips">
            {autoresNuevos.map((n) => (
              <span key={'n-' + n} className="chip on chip-nuevo" title="Autor nuevo: clic para quitarlo" onClick={() => quitarDeLista(setAutoresNuevos, n)}>
                ＋ {n}
              </span>
            ))}
            {autores.map((a) => (
              <span key={a.id} className={'chip' + (f.ids_autores.includes(a.id) ? ' on' : '')} onClick={() => alternar('ids_autores', a.id)}>
                {a.nombre}
              </span>
            ))}
            {autores.length === 0 && autoresNuevos.length === 0 && <small>Todavía no hay autores: escribe el nombre abajo.</small>}
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 6 }}>
            <input
              className="input"
              placeholder="Otro autor (nombre completo)..."
              value={nuevoAutor}
              maxLength={150}
              onChange={(e) => setNuevoAutor(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); agregarAutor(); } }}
            />
            <button type="button" className="btn-ghost" onClick={agregarAutor}>Agregar</button>
          </div>
        </Campo>

        {/* ---- Generos ---- */}
        <Campo
          label={`Géneros (${f.ids_generos.length + generosNuevos.length} seleccionados)`}
          ancho={2}
          ayuda={generosNuevos.length ? 'Los marcados con ＋ son nuevos: se crean solos al guardar el libro.' : undefined}
        >
          <div className="chips">
            {generosNuevos.map((n) => (
              <span key={'n-' + n} className="chip on chip-nuevo" title="Género nuevo: clic para quitarlo" onClick={() => quitarDeLista(setGenerosNuevos, n)}>
                ＋ {n}
              </span>
            ))}
            {generos.map((g) => (
              <span key={g.id} className={'chip' + (f.ids_generos.includes(g.id) ? ' on' : '')} onClick={() => alternar('ids_generos', g.id)}>
                {g.nombre}
              </span>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 6 }}>
            <input
              className="input"
              placeholder="Nuevo género..."
              value={nuevoGenero}
              maxLength={80}
              onChange={(e) => setNuevoGenero(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); agregarGenero(); } }}
            />
            <button type="button" className="btn-ghost" onClick={agregarGenero}>Agregar</button>
          </div>
        </Campo>
      </form>
    </Modal>
  );
}

export default FormLibro;
