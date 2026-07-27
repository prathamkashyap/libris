const m = { "&": "&amp;", "<": "&lt;", ">": "&gt;", "'": "&#39;", '"': "&quot;" };
export const esc = v => String(v ?? "").replace(/[&<>'"]/g, c => m[c]);
