package com.example.lms.dto;
import java.time.LocalDate;
import java.util.List;
public record OverdueSummaryResponse(long totalOverdue, List<OverdueItem> items){
    public record OverdueItem(Long id, String itemTitle, String borrowerName, LocalDate borrowDate, long daysOverdue){}
}
