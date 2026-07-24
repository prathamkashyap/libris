import { requestJson } from "/js/api/http.js";
export const borrowApi={list:()=>requestJson("/api/borrow-records"),create:data=>requestJson("/api/borrow-records",{method:"POST",body:JSON.stringify(data)}),returnBook:id=>requestJson(`/api/borrow-records/${id}/return`,{method:"POST"})};
