// Protege rutas: exige sesión y, si se indica, un rol.
// soloAdmin = bibliotecario o gerente · soloGerente = gerente.
// permitirInvitado = la única pantalla que puede ver el rol visual "invitado".
import { Navigate } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';

function ProtectedRoute({ children, soloAdmin = false, soloGerente = false, permitirInvitado = false }) {
  const { usuario, esAdmin, esGerente, esInvitado } = useAuth();

  if (!usuario) return <Navigate to="/login" replace />;
  // El invitado no tiene cuenta real: cualquier sección lo regresa a Inicio.
  if (esInvitado && !permitirInvitado) return <Navigate to="/inicio" replace />;
  if (soloAdmin && !esAdmin) return <Navigate to="/inicio" replace />;
  if (soloGerente && !esGerente) return <Navigate to="/inicio" replace />;

  return children;
}

export default ProtectedRoute;
