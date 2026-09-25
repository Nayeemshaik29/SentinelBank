import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'com.sentinelbank.app',
  appName: 'SentinelBank',
  webDir: 'dist/sentinelbank-web/browser',
  server: {
    // Capacitor's default is 'https', which makes the app's own origin https://localhost — any plain
    // http:// XHR (like the emulator-testing backend at http://10.0.2.2:8080, see
    // environment.mobile.ts) is then blocked by the WebView as mixed content, separately from Android's
    // OS-level cleartext policy (network_security_config.xml). 'http' avoids that for this
    // local-testing phase. Switch this back to 'https' (or remove the override) once the app points at
    // a real HTTPS backend — there is no mixed-content concern once both sides are https.
    androidScheme: 'http',
  },
};

export default config;
