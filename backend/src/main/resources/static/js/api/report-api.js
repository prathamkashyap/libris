export const reportApi={
    inventoryUrl:()=>"/api/reports/inventory?format=csv",
    borrowingUrl:(from,to)=>{const p=new URLSearchParams();p.set("format","csv");if(from)p.set("from",from);if(to)p.set("to",to);return `/api/reports/borrowing?${p}`;},
    studentsUrl:()=>"/api/reports/students?format=csv",
    download:url=>{const a=document.createElement("a");a.href=url;a.click();}
};
