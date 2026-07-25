import { requestJson } from "/js/api/http.js";
export const studentDashboardApi = {
    getDashboard: () => requestJson("/api/student/dashboard"),
    getBorrowHistory: (status = null, page = 0, size = 10) => {
        const params = new URLSearchParams();
        if (status) params.set('status', status);
        params.set('page', page);
        params.set('size', size);
        return requestJson(`/api/student/borrow-history?${params.toString()}`);
    },
    getProfile: () => requestJson("/api/student/profile")
};