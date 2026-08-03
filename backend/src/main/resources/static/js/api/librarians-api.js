import { requestJson } from "/js/api/http.js";
export const librariansApi = {
    list: (page = 0, size = 10) => requestJson(`/api/librarians?page=${page}&size=${size}`),
    get: (id) => requestJson(`/api/librarians/${id}`),
    create: data => requestJson("/api/librarians", { method: "POST", body: JSON.stringify(data) }),
    update: (id, data) => requestJson(`/api/librarians/${id}`, { method: "PUT", body: JSON.stringify(data) }),
    delete: (id) => requestJson(`/api/librarians/${id}`, { method: "DELETE" })
};
