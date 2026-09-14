export const reportApi={
    inventoryUrl:()=>"/api/reports/inventory",
    borrowingUrl:(from,to)=>{const p=new URLSearchParams();if(from)p.set("from",from);if(to)p.set("to",to);return p.toString()?`/api/reports/borrowing?${p}`:"/api/reports/borrowing";},
    studentsUrl:()=>"/api/reports/students",
    download:url=>{const a=document.createElement("a");a.href=url;a.click();}
};
