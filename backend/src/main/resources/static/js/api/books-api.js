import { requestJson } from "/js/api/http.js";
export const booksApi = {
    list: (search = "", page = 0, size = 10) => requestJson(`/api/books?page=${page}&size=${size}${search ? `&search=${encodeURIComponent(search)}` : ""}`),
    get: (id) => requestJson(`/api/books/${id}`),
    create: data => requestJson("/api/books", { method: "POST", body: JSON.stringify(data) }),
    update: (id, data) => requestJson(`/api/books/${id}`, { method: "PUT", body: JSON.stringify(data) }),
    delete: (id) => requestJson(`/api/books/${id}`, { method: "DELETE" })
};
