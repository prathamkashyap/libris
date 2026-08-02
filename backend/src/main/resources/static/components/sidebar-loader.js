/**
 * Loads the shared sidebar into the <nav class="rail"> element.
 * Fetches /components/sidebar.html once and caches the result.
 */

let _cache = null;

export async function loadSidebar() {
  const rail = document.getElementById('rail');
  if (!rail) return;

  try {
    if (!_cache) {
      const res = await fetch('/components/sidebar.html');
      if (!res.ok) throw new Error(`Sidebar fetch failed: ${res.status}`);
      _cache = await res.text();
    }
    rail.innerHTML = _cache;
  } catch (err) {
    console.error('Sidebar load failed:', err);
  }
}
