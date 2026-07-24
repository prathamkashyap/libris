import { requestJson } from "/js/api/http.js";
export const studentsApi={list:()=>requestJson("/api/students"),create:data=>requestJson("/api/students",{method:"POST",body:JSON.stringify(data)})};
