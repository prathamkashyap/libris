import { requestJson } from "/js/api/http.js";
export const studentsApi = {
    list: (page = 0, size = 10, query = "") => requestJson(`/api/students?page=${page}&size=${size}${query ? `&query=${encodeURIComponent(query)}` : ""}`),
    get: (id) => requestJson(`/api/students/${id}`),
    create: data => requestJson("/api/students", { method: "POST", body: JSON.stringify(data) }),
    update: (id, data) => requestJson(`/api/students/${id}`, { method: "PUT", body: JSON.stringify(data) }),
    delete: (id) => requestJson(`/api/students/${id}`, { method: "DELETE" })
};
