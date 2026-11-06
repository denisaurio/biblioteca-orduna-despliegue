import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom';
import ProtectedRoute from './ProtectedRoute.jsx';
import AppLayout from '../components/layout/AppLayout.jsx';
import { useAuth } from '../context/AuthContext.jsx';

import Login from '../pages/Login/Login.jsx';
import Registro from '../pages/Login/Registro.jsx';
import Inicio from '../pages/Inicio/Inicio.jsx';
import Libros from '../pages/Libros/Libros.jsx';
import Autores from '../pages/Autores/Autores.jsx';
import Usuarios from '../pages/Usuarios/Usuarios.jsx';
import Prestamos from '../pages/Prestamos/Prestamos.jsx';
import MisPrestamos from '../pages/Prestamos/MisPrestamos.jsx';
import Devoluciones from '../pages/Devoluciones/Devoluciones.jsx';
import Reservas from '../pages/Reservas/Reservas.jsx';
import MisReservas from '../pages/Reservas/MisReservas.jsx';
import Ejemplares from '../pages/Ejemplares/Ejemplares.jsx';
import PagosMulta from '../pages/PagosMulta/PagosMulta.jsx';
import Sanciones from '../pages/Sanciones/Sanciones.jsx';
import Avisos from '../pages/Avisos/Avisos.jsx';
import Bitacora from '../pages/Bitacora/Bitacora.jsx';
import Reportes from '../pages/Reportes/Reportes.jsx';
import Configuracion from '../pages/Configuracion/Configuracion.jsx';
import Perfil from '../pages/Perfil/Perfil.jsx';
import LibrosVirtuales from '../pages/LibrosVirtuales/LibrosVirtuales.jsx';
import MisLibrosVirtuales from '../pages/LibrosVirtuales/MisLibrosVirtuales.jsx';
import Caja from '../pages/Caja/Caja.jsx';
import Auditoria from '../pages/Auditoria/Auditoria.jsx';

function conLayout(Pagina, opciones) {
  return (
    <ProtectedRoute {...opciones}>
      <AppLayout>
        <Pagina />
      </AppLayout>
    </ProtectedRoute>
  );
}

/** Préstamos y Reservas muestran una pantalla distinta para el lector. */
function SegunRol({ lector: Lector, personal: Personal }) {
  const { esLector } = useAuth();
  return esLector ? <Lector /> : <Personal />;
}

const PrestamosSegunRol = () => <SegunRol lector={MisPrestamos} personal={Prestamos} />;
const ReservasSegunRol = () => <SegunRol lector={MisReservas} personal={Reservas} />;

function AppRoutes() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/registro" element={<Registro />} />

        <Route path="/inicio" element={conLayout(Inicio)} />
        <Route path="/libros" element={conLayout(Libros)} />
        <Route path="/autores" element={conLayout(Autores)} />
        <Route path="/prestamos" element={conLayout(PrestamosSegunRol)} />
        <Route path="/reservas" element={conLayout(ReservasSegunRol)} />
        <Route path="/avisos" element={conLayout(Avisos)} />
        <Route path="/perfil" element={conLayout(Perfil)} />
        <Route path="/mis-libros-virtuales" element={conLayout(MisLibrosVirtuales)} />

        {/* Bibliotecario o gerente */}
        <Route path="/usuarios" element={conLayout(Usuarios, { soloAdmin: true })} />
        <Route path="/devoluciones" element={conLayout(Devoluciones, { soloAdmin: true })} />
        <Route path="/ejemplares" element={conLayout(Ejemplares, { soloAdmin: true })} />
        <Route path="/pagos-multa" element={conLayout(PagosMulta, { soloAdmin: true })} />
        <Route path="/libros-virtuales" element={conLayout(LibrosVirtuales, { soloAdmin: true })} />
        <Route path="/sanciones" element={conLayout(Sanciones, { soloAdmin: true })} />
        <Route path="/caja" element={conLayout(Caja, { soloAdmin: true })} />
        <Route path="/bitacora" element={conLayout(Bitacora, { soloAdmin: true })} />
        <Route path="/reportes" element={conLayout(Reportes, { soloAdmin: true })} />

        {/* Solo gerente */}
        <Route path="/configuracion" element={conLayout(Configuracion, { soloGerente: true })} />
        <Route path="/auditoria" element={conLayout(Auditoria, { soloGerente: true })} />

        <Route path="/" element={<Navigate to="/inicio" replace />} />
        <Route path="*" element={<Navigate to="/inicio" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default AppRoutes;
