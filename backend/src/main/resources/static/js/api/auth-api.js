import { requestJson } from "/js/api/http.js";
export const authApi={csrf:()=>requestJson("/api/auth/csrf"),login:data=>requestJson("/api/auth/login",{method:"POST",body:JSON.stringify(data)}),logout:()=>requestJson("/api/auth/logout",{method:"POST"}),me:()=>requestJson("/api/auth/me"),profile:()=>requestJson("/api/profile")};
