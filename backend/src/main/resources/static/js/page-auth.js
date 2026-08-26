import { authApi } from "/js/api/auth-api.js";
import { setCurrentUser } from "/js/api/http.js";

document.addEventListener("DOMContentLoaded", async () => {
  try {
    await authApi.csrf();
    setCurrentUser(await authApi.me());
  } catch {
    // requestJson redirects expired sessions to the sign-in page.
  }
});
