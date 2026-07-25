import { requestJson } from "/js/api/http.js";
export const magazinesApi = {
    list: (search = "", page = 0, size = 10) => requestJson(`/api/magazines?page=${page}&size=${size}${search ? `&search=${encodeURIComponent(search)}` : ""}`),
    get: (id) => requestJson(`/api/magazines/${id}`),
    create: data => requestJson("/api/magazines", { method: "POST", body: JSON.stringify(data) }),
    update: (id, data) => requestJson(`/api/magazines/${id}`, { method: "PUT", body: JSON.stringify(data) }),
    delete: (id) => requestJson(`/api/magazines/${id}`, { method: "DELETE" })
};
