/**
 * MediTriaje 2.0 — Gestión de Autenticación y Sesión (auth.js)
 * Manejo de estado de usuario exclusivamente en memoria (cero tokens en localStorage).
 * Compatible con roles ROLE_PACIENTE, ROLE_PROFESIONAL, ROLE_ADMINISTRADOR (ADR-002).
 */

import { api } from './api.js';

class AuthService extends EventTarget {
  constructor() {
    super();
    this.currentUser = null;
    this.isInitialized = false;

    // Escuchar expiración global de sesión desde el cliente API
    window.addEventListener('auth:session-expired', () => {
      this.handleSessionExpired();
    });
  }

  get isAuthenticated() {
    return this.currentUser !== null;
  }

  get user() {
    return this.currentUser;
  }

  get roles() {
    return this.currentUser?.roles || [];
  }

  hasRole(role) {
    return this.roles.includes(role);
  }

  get isPaciente() {
    return this.hasRole('ROLE_PACIENTE');
  }

  get isProfesional() {
    return this.hasRole('ROLE_PROFESIONAL');
  }

  get isAdmin() {
    return this.hasRole('ROLE_ADMINISTRADOR');
  }

  /**
   * Inicializa la sesión intentando refrescar credenciales de las cookies HttpOnly existentes.
   */
  async init() {
    if (this.isInitialized) return this.currentUser;

    try {
      const session = await api.post('/auth/refresh');
      if (session && session.publicId) {
        this.setUserFromSession(session);
      }
    } catch {
      // Sesión no existente o cookies inválidas; usuario permanece como invitado
      this.currentUser = null;
    } finally {
      this.isInitialized = true;
      this.emitChange();
    }

    return this.currentUser;
  }

  /**
   * Inicia sesión con correo y contraseña.
   */
  async login(email, password) {
    const session = await api.post('/auth/login', { email, password });
    this.setUserFromSession(session);
    this.emitChange();
    return session;
  }

  /**
   * Registra un nuevo paciente en una transacción atómica.
   */
  async register(patientData) {
    const session = await api.post('/auth/register', patientData);
    this.setUserFromSession(session);
    this.emitChange();
    return session;
  }

  /**
   * Cierra la sesión activa en el servidor y limpia el estado local.
   */
  async logout() {
    try {
      await api.post('/auth/logout');
    } catch {
      // Continuar con limpieza local incluso ante fallas de red
    } finally {
      this.currentUser = null;
      this.emitChange();
      window.location.hash = '#/login';
    }
  }

  /**
   * Cambia la contraseña obligatoria del primer acceso o voluntaria.
   */
  async changePassword(passwordActual, passwordNuevo) {
    await api.post('/auth/change-password', { passwordActual, passwordNuevo });
    if (this.currentUser) {
      this.currentUser.debeCambiarPassword = false;
      this.emitChange();
    }
  }

  /**
   * Limpia la sesión tras expiración comprobada del refresh token.
   */
  handleSessionExpired() {
    if (this.currentUser) {
      this.currentUser = null;
      this.emitChange();
      window.location.hash = '#/login';
    }
  }

  setUserFromSession(session) {
    this.currentUser = {
      publicId: session.publicId,
      email: session.email,
      roles: session.roles || [],
      debeCambiarPassword: Boolean(session.debeCambiarPassword)
    };
  }

  emitChange() {
    this.dispatchEvent(new CustomEvent('auth:change', {
      detail: {
        isAuthenticated: this.isAuthenticated,
        user: this.currentUser
      }
    }));
  }
}

export const auth = new AuthService();
