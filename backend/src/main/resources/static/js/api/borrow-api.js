import { requestJson } from "/js/api/http.js";
export const borrowApi={list:(page=0,size=10,query="")=>requestJson(`/api/borrow-records?page=${page}&size=${size}${query?`&query=${encodeURIComponent(query)}`:""}`),create:data=>requestJson("/api/borrow-records",{method:"POST",body:JSON.stringify(data)}),returnBook:id=>requestJson(`/api/borrow-records/${id}/return`,{method:"POST"})};
