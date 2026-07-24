import { requestJson } from "/js/api/http.js";
export const booksApi={list:(search="")=>requestJson(`/api/books${search?`?search=${encodeURIComponent(search)}`:""}`),create:data=>requestJson("/api/books",{method:"POST",body:JSON.stringify(data)})};
