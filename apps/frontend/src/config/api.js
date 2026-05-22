/** API base URL: build-time (Vite) or runtime /config.json after deploy. */
export let apiBaseUrl = import.meta.env.VITE_API_URL || '';

export async function loadRuntimeConfig() {
  try {
    const res = await fetch('/config.json', { cache: 'no-store' });
    if (!res.ok) return;
    const data = await res.json();
    if (data.VITE_API_URL) {
      apiBaseUrl = data.VITE_API_URL.replace(/\/$/, '');
    }
  } catch {
    /* static hosting without config.json */
  }
}
