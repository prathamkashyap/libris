package com.example.lms.util;

import com.example.lms.entity.BorrowRecord;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public final class OverdueCalculator {

  private OverdueCalculator() {}

  public static LocalDate effectiveDueDate(BorrowRecord record) {
    if (record == null) {
      return null;
    }
    if (record.getDueDate() != null) {
      return record.getDueDate();
    }
    return record.getBorrowDate() != null ? record.getBorrowDate().plusDays(14) : null;
  }

  public static boolean isOverdue(BorrowRecord record) {
    if (record == null) {
      return false;
    }
    LocalDate dueDate = effectiveDueDate(record);
    if (dueDate == null) {
      return false;
    }
    LocalDate compareDate =
        record.getReturnDate() != null ? record.getReturnDate() : LocalDate.now();
    return compareDate.isAfter(dueDate);
  }

  public static long daysOverdue(BorrowRecord record) {
    if (record == null) {
      return 0L;
    }
    LocalDate dueDate = effectiveDueDate(record);
    if (dueDate == null) {
      return 0L;
    }
    LocalDate compareDate =
        record.getReturnDate() != null ? record.getReturnDate() : LocalDate.now();
    if (compareDate.isAfter(dueDate)) {
      return ChronoUnit.DAYS.between(dueDate, compareDate);
    }
    return 0L;
  }
}
