export const environment = {
  production: true,
  // Capacitor apps have no same-origin server to proxy /api the way the web build's nginx container
  // does (see environment.ts) — the WebView needs an absolute URL to the gateway instead.
  //
  // 10.0.2.2 is the Android emulator's own alias for the host machine's localhost — this points the
  // app at whatever backend is running locally via `docker compose up` (see infra/docker-compose.yml)
  // for on-device testing before there's a real public deployment. A physical phone on the same Wi-Fi
  // needs the host machine's actual LAN IP instead (e.g. http://192.168.1.23:8080/api). Once the
  // backend has a real public URL, replace this with that (HTTPS — see README's Android section).
  apiBaseUrl: 'http://10.0.2.2:8080/api',
};
