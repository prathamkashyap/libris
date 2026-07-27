import { requestJson } from "/js/api/http.js";
export const analyticsApi={
    dashboard:()=>requestJson("/api/analytics/dashboard"),
    trends:()=>requestJson("/api/analytics/trends"),
    topBooks:(limit=5)=>requestJson(`/api/analytics/top-books?limit=${limit}`),
    topReaders:(limit=5)=>requestJson(`/api/analytics/top-readers?limit=${limit}`),
    overdue:()=>requestJson("/api/analytics/overdue")
};
