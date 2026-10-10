/**
 * MediTriaje 2.0 — Enrutador SPA Hash-based (router.js)
 * Enrutamiento del lado del cliente sin dependencias externas, con soporte
 * para guardias de navegación (autenticación y roles requeridos).
 */

import { auth } from './auth.js';

class Router {
  constructor() {
    this.routes = [];
    this.notFoundHandler = null;
    this.forbiddenHandler = null;
    this.currentPath = '';
    this.appContainer = null;

    window.addEventListener('hashchange', () => this.handleRoute());
  }

  /**
   * Asocia el contenedor principal del DOM para renderizar las vistas.
   */
  setContainer(container) {
    this.appContainer = container;
  }

  /**
   * Registra una ruta en el enrutador.
   *
   * @param {string} path Ruta con soporte de parámetros (/citas/:id)
   * @param {Function} handler Función asíncrona que renderiza la vista en el contenedor
   * @param {Object} meta Metadatos: requiresAuth, requiredRole, guestOnly
   */
  addRoute(path, handler, meta = {}) {
    // Convertir ruta con parámetros (:id) a expresión regular
    const paramNames = [];
    const regexPath = path.replace(/:([a-zA-Z0-9_-]+)/g, (_, name) => {
      paramNames.push(name);
      return '([a-zA-Z0-9_-]+)';
    });

    const regex = new RegExp(`^${regexPath}$`);

    this.routes.push({
      path,
      regex,
      paramNames,
      handler,
      meta
    });
  }

  notFound(handler) {
    this.notFoundHandler = handler;
  }

  forbidden(handler) {
    this.forbiddenHandler = handler;
  }

  navigate(path) {
    window.location.hash = path.startsWith('#') ? path : `#${path}`;
  }

  /**
   * Inicia la escucha y procesa la ruta inicial.
   */
  async start() {
    await this.handleRoute();
  }

  /**
   * Evalúa la ruta actual del hash y ejecuta los guardias de seguridad y renderizado.
   */
  async handleRoute() {
    const rawHash = window.location.hash.slice(1) || '/';
    const [pathPart, queryString] = rawHash.split('?');
    const path = pathPart.startsWith('/') ? pathPart : `/${pathPart}`;
    this.currentPath = path;

    const queryParams = new URLSearchParams(queryString || '');

    // Buscar coincidencia en la tabla de rutas
    let matchedRoute = null;
    let params = {};

    for (const route of this.routes) {
      const match = path.match(route.regex);
      if (match) {
        matchedRoute = route;
        route.paramNames.forEach((name, index) => {
          params[name] = match[index + 1];
        });
        break;
      }
    }

    if (!matchedRoute) {
      const anchorId = rawHash.replace(/^\//, '');
      const targetEl = document.getElementById(anchorId);
      if (targetEl) {
        targetEl.scrollIntoView({ behavior: 'smooth' });
        return;
      }
      if (this.notFoundHandler) {
        await this.notFoundHandler(this.appContainer, { path });
      }
      return;
    }

    const { meta, handler } = matchedRoute;

    // Guardia 0: Usuarios autenticados que visitan la raíz pública son redirigidos a su respectivo panel
    if (path === '/' && auth.isAuthenticated) {
      this.redirectToHome();
      return;
    }

    // Guardia 1: Rutas exclusivas para invitados (login, registro)
    if (meta.guestOnly && auth.isAuthenticated) {
      this.redirectToHome();
      return;
    }

    // Guardia 2: Rutas que requieren autenticación
    if (meta.requiresAuth && !auth.isAuthenticated) {
      this.navigate(`/login?redirect=${encodeURIComponent(path)}`);
      return;
    }

    // Guardia 3: Rutas con rol específico obligatorio
    if (meta.requiredRole && !auth.hasRole(meta.requiredRole)) {
      if (this.forbiddenHandler) {
        await this.forbiddenHandler(this.appContainer, { path, requiredRole: meta.requiredRole });
      } else {
        this.redirectToHome();
      }
      return;
    }

    // Ejecutar renderizado de la vista
    try {
      if (this.appContainer) {
        this.appContainer.innerHTML = '';
      }
      await handler(this.appContainer, { params, queryParams, path });
      window.scrollTo(0, 0);
    } catch (err) {
      console.error('Error al renderizar ruta:', err);
    }
  }

  /**
   * Redirecciona al panel principal correspondiente al rol del usuario.
   */
  redirectToHome() {
    if (auth.isPaciente) {
      this.navigate('/patient/dashboard');
    } else if (auth.isProfesional) {
      this.navigate('/professional/agenda');
    } else if (auth.isAdmin) {
      this.navigate('/admin/dashboard');
    } else if (auth.isFarmaceutico) {
      this.navigate('/pharmacy/dispensation');
    } else if (auth.isEnfermeria) {
      this.navigate('/nursing/dashboard');
    } else {
      this.navigate('/');
    }
  }
}

export const router = new Router();
