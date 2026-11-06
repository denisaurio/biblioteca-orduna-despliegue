// Portada del libro: la imagen guardada en Cloudflare R2 si existe; si no,
// la portada generada (color + título), al estilo de "Libros destacados" del prototipo.
//
// El link puede ser la URL pública de R2 (https://pub-xxxx.r2.dev/...) o la ruta del
// backend (/api/archivos/portadas/...) cuando todavía no hay URL pública: urlArchivo()
// le pone el host del backend. Las imágenes cargan perezosas (loading="lazy"): con un
// catálogo grande solo se piden las que se ven en pantalla.
import { useEffect, useState } from 'react';
import { colorPortada } from '../../utils/formato.js';
import { urlArchivo } from '../../api/axiosClient.js';

function Portada({ libro, alto = 140 }) {
  const [fallo, setFallo] = useState(false);
  const url = urlArchivo(libro?.url_imagen);

  // Si cambia la portada (se subió otra), se vuelve a intentar.
  useEffect(() => setFallo(false), [url]);

  if (url && !fallo) {
    return (
      <div className="portada" style={{ background: '#8C8577', height: alto, padding: 0, overflow: 'hidden' }}>
        <img
          src={url}
          alt={libro.titulo}
          loading="lazy"
          decoding="async"
          style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          onError={() => setFallo(true)}
        />
      </div>
    );
  }

  return (
    <div className="portada" style={{ background: colorPortada(libro.id), height: alto }}>
      <span className="portada-titulo">{libro.titulo}</span>
      {libro.anio_publicacion && <span className="portada-anio">{libro.anio_publicacion}</span>}
    </div>
  );
}

export default Portada;
