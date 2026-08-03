import { requestJson } from "/js/api/http.js";
export const newspapersApi = {
    list: (search = "", page = 0, size = 10) => requestJson(`/api/newspapers?page=${page}&size=${size}${search ? `&search=${encodeURIComponent(search)}` : ""}`),
    get: (id) => requestJson(`/api/newspapers/${id}`),
    create: data => requestJson("/api/newspapers", { method: "POST", body: JSON.stringify(data) }),
    update: (id, data) => requestJson(`/api/newspapers/${id}`, { method: "PUT", body: JSON.stringify(data) }),
    delete: (id) => requestJson(`/api/newspapers/${id}`, { method: "DELETE" })
};
