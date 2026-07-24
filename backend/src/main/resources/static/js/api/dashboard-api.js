import { requestJson } from "/js/api/http.js";
export const dashboardApi={get:()=>requestJson("/api/dashboard")};
