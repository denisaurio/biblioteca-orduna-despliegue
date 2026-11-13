// Animación de carga de toda la app: un libro pasando páginas, el símbolo de
// carga y una frase motivadora. Se muestra al abrir la página y en cada cambio
// de ruta (ver routes/AppRoutes.jsx).
import { useMemo } from 'react';
import './CargadorLibro.css';

const FRASES = [
  'Cada página te acerca a una nueva idea.',
  'Leer es soñar con los ojos abiertos.',
  'Un libro es un amigo que nunca te abandona.',
  'Hoy es un buen día para aprender algo nuevo.',
  'Los libros son ventanas a otros mundos.',
  'La lectura abre puertas que nadie puede cerrar.',
  'Cada gran historia empieza con una página.',
  'Crecer también es leer.',
  'Quien lee, nunca está solo.',
  'Un buen libro siempre deja huella.',
];

/**
 * @param {object} props
 * @param {string} [props.frase]  Frase fija; si no se indica se elige una al azar.
 * @param {boolean} [props.saliendo]  Añade la animación de despedida antes de desmontar.
 */
function CargadorLibro({ frase, saliendo = false }) {
  // useMemo para que la frase no cambie en cada render mientras se ve el cargador.
  const mensaje = useMemo(
    () => frase || FRASES[Math.floor(Math.random() * FRASES.length)],
    [frase],
  );

  return (
    <div className={'cargador-libro' + (saliendo ? ' saliendo' : '')} role="status" aria-live="polite">
      <div className="cargador-libro-centro">
        <div className="libro-carga" aria-hidden="true">
          <span className="libro-carga-tapa" />
          <span className="libro-carga-pagina" />
          <span className="libro-carga-hoja hoja-1" />
          <span className="libro-carga-hoja hoja-2" />
          <span className="libro-carga-hoja hoja-3" />
          <span className="libro-carga-lomo" />
        </div>

        <div className="cargador-puntos" aria-hidden="true">
          <span />
          <span />
          <span />
        </div>

        <p className="cargador-frase">{mensaje}</p>
      </div>
    </div>
  );
}

export default CargadorLibro;
