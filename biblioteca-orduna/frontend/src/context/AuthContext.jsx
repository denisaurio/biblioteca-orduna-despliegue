// Sesión del usuario logueado (JWT del backend) y su rol: LECTOR, BIBLIOTECARIO o GERENTE.
import { createContext, useContext, useState } from 'react';
import { login as loginApi } from '../api/authApi.js';
import { leerSesion, guardarSesion, borrarSesion } from '../utils/sesion.js';

const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const [sesion, setSesion] = useState(() => leerSesion());

  /** Llama a POST /api/auth/login y guarda { token, usuario }. Lanza el error de axios si falla. */
  const login = async (email, password, recordar) => {
    const { data } = await loginApi(email, password);
    const nueva = {
      token: data.token,
      usuario: { id: data.id_usuario, nombre: data.nombre, email: data.email, rol: data.rol },
    };
    guardarSesion(nueva, recordar);
    setSesion(nueva);
    return nueva.usuario;
  };

  /**
   * "Entrar como invitado": crea una sesión SOLO en memoria, sin token y sin
   * guardarla en localStorage. Es un modo visual para conocer el sistema; el
   * backend nunca recibe este rol.
   */
  const entrarComoInvitado = () => {
    setSesion({
      invitado: true,
      token: null,
      usuario: { id: null, nombre: 'Invitado', email: '', rol: 'INVITADO' },
    });
  };

  const logout = () => {
    borrarSesion();
    setSesion(null);
  };

  /** Actualiza el nombre mostrado después de editar el perfil. */
  const actualizarNombre = (nombre) => {
    // El invitado nunca se guarda: su sesión vive solo en memoria.
    if (!sesion || sesion.invitado) return;
    const nueva = { ...sesion, usuario: { ...sesion.usuario, nombre } };
    guardarSesion(nueva, !!localStorage.getItem('biblioteca_sesion'));
    setSesion(nueva);
  };

  const usuario = sesion?.usuario || null;
  const rol = usuario?.rol;
  const esInvitado = sesion?.invitado === true || rol === 'INVITADO';
  const valor = {
    usuario,
    token: sesion?.token || null,
    login,
    logout,
    entrarComoInvitado,
    actualizarNombre,
    esInvitado,
    esLector: rol === 'LECTOR',
    esAdmin: rol === 'BIBLIOTECARIO' || rol === 'GERENTE', // bibliotecario o gerente (bloque Administración)
    esGerente: rol === 'GERENTE',
  };

  return <AuthContext.Provider value={valor}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
