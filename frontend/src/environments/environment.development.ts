export const environment = {
  production: false,
  // The gateway (see README) is what strips /api and routes everywhere; the Angular dev server has no
  // proxy configured, so this points straight at it running on its default local port.
  apiBaseUrl: 'http://localhost:8080/api',
};
