import { requestJson } from "/js/api/http.js";
export const librariansApi={list:()=>requestJson("/api/librarians"),create:data=>requestJson("/api/librarians",{method:"POST",body:JSON.stringify(data)})};
