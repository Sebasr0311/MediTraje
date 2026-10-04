/**
 * MediTriaje 2.0 — Cliente API Centralizado (api.js)
 * Manejo uniforme de fetch, cookies HttpOnly, cabecera CSRF (ADR-002)
 * e interceptor automático de refresco de tokens (401).
 */

export class ApiError extends Error {
  constructor(status, code, message, details = {}, traceId = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code || 'ERROR_DESCONOCIDO';
    this.details = details;
    this.traceId = traceId;
  }
}

class ApiClient {
  constructor() {
    this.baseUrl = '/api/v1';
    this.isRefreshing = false;
    this.refreshSubscribers = [];
  }

  /**
   * Suscribe callbacks para reintentar peticiones en cola tras el refresco del token.
   */
  onRefreshed() {
    this.refreshSubscribers.forEach(cb => cb());
    this.refreshSubscribers = [];
  }

  /**
   * Ejecuta una petición HTTP con credenciales (cookies), cabecera CSRF y manejo central de errores.
   *
   * @param {string} endpoint Ruta relativa o absoluta a la API
   * @param {RequestInit} options Opciones estándar de fetch
   * @returns {Promise<any>} Respuesta deserializada de JSON (o null en 204 No Content)
   */
  async request(endpoint, options = {}) {
    const url = endpoint.startsWith('http') ? endpoint : `${this.baseUrl}${endpoint.startsWith('/') ? '' : '/'}${endpoint}`;

    const headers = new Headers(options.headers || {});
    headers.set('Accept', 'application/json');

    // Cabecera CSRF obligatoria para peticiones mutantes según CsrfHeaderFilter (ADR-002)
    const method = (options.method || 'GET').toUpperCase();
    if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
      headers.set('X-Requested-With', 'XMLHttpRequest');
    }

    if (options.body && typeof options.body === 'object' && !(options.body instanceof FormData)) {
      headers.set('Content-Type', 'application/json');
      options.body = JSON.stringify(options.body);
    }

    // Configuración obligatoria: transmisión de cookies HttpOnly (access_token, refresh_token)
    options.credentials = 'include';
    options.headers = headers;

    let response;
    try {
      response = await fetch(url, options);
    } catch (networkError) {
      throw new ApiError(
        0,
        'ERROR_CONEXION',
        'No fue posible comunicarse con el servidor. Revisa tu conexion a internet.',
        networkError
      );
    }

    // Caso 204 No Content (p. ej. DELETE)
    if (response.status === 204) {
      return null;
    }

    // Manejo de expiración de Access Token (401 Unauthorized) con reintento automático
    const isAuthEndpoint = endpoint.includes('/auth/login') ||
                           endpoint.includes('/auth/register') ||
                           endpoint.includes('/auth/refresh') ||
                           endpoint.includes('/auth/logout');

    if (response.status === 401 && !isAuthEndpoint) {
      if (!this.isRefreshing) {
        this.isRefreshing = true;
        try {
          // Intentar renovar el access_token mediante el refresh_token de la cookie
          const refreshRes = await fetch(`${this.baseUrl}/auth/refresh`, {
            method: 'POST',
            credentials: 'include',
            headers: {
              'Accept': 'application/json',
              'X-Requested-With': 'XMLHttpRequest'
            }
          });

          if (refreshRes.ok) {
            this.isRefreshing = false;
            this.onRefreshed();
            // Reintentar la petición original con la nueva sesión
            return this.request(endpoint, options);
          } else {
            // El refresh token expiró o fue revocado
            this.isRefreshing = false;
            this.refreshSubscribers = [];
            window.dispatchEvent(new CustomEvent('auth:session-expired'));
          }
        } catch (e) {
          this.isRefreshing = false;
          this.refreshSubscribers = [];
          window.dispatchEvent(new CustomEvent('auth:session-expired'));
        }
      } else {
        // Encolar petición mientras se completa el refresco activo
        return new Promise((resolve, reject) => {
          this.refreshSubscribers.push(() => {
            this.request(endpoint, options).then(resolve).catch(reject);
          });
        });
      }
    }

    // Procesar cuerpo de la respuesta
    const contentType = response.headers.get('content-type') || '';
    const isJson = contentType.includes('application/json');
    const data = isJson ? await response.json().catch(() => ({})) : await response.text();

    if (!response.ok) {
      const code = data?.codigo || `HTTP_${response.status}`;
      const message = data?.mensaje || (typeof data === 'string' ? data : 'Ocurrio un error al procesar la solicitud.');
      throw new ApiError(response.status, code, message, data, data?.traceId);
    }

    return data;
  }

  get(endpoint, params = null) {
    let url = endpoint;
    if (params) {
      const searchParams = new URLSearchParams();
      for (const [key, value] of Object.entries(params)) {
        if (value !== null && value !== undefined && value !== '') {
          searchParams.append(key, value);
        }
      }
      const queryString = searchParams.toString();
      if (queryString) {
        url += (url.includes('?') ? '&' : '?') + queryString;
      }
    }
    return this.request(url, { method: 'GET' });
  }

  post(endpoint, body = {}) {
    return this.request(endpoint, { method: 'POST', body });
  }

  put(endpoint, body = {}) {
    return this.request(endpoint, { method: 'PUT', body });
  }

  patch(endpoint, body = {}) {
    return this.request(endpoint, { method: 'PATCH', body });
  }

  delete(endpoint, body = null) {
    return this.request(endpoint, { method: 'DELETE', body });
  }
}

export const api = new ApiClient();
