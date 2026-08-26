import "/js/page-auth.js";
import { getTheme, toggleTheme } from "/js/theme.js";

function renderTheme() {
  const isLight = getTheme() === "verdigris";
  document.getElementById("currentTheme").textContent = isLight ? "Light" : "Dark";
  document.getElementById("toggleThemeButton").textContent = isLight ? "Use dark theme" : "Use light theme";
}

document.addEventListener("DOMContentLoaded", () => {
  renderTheme();
  window.addEventListener("themechange", renderTheme);
  document.getElementById("toggleThemeButton")?.addEventListener("click", () => {
    toggleTheme();
    renderTheme();
  });
});
