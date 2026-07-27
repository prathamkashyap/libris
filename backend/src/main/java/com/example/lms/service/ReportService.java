package com.example.lms.service;

import com.example.lms.repository.*;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class ReportService {
    private final BookRepository books;
    private final MagazineRepository magazines;
    private final NewspaperRepository newspapers;
    private final BorrowRecordRepository borrowRecords;
    private final StudentProfileRepository students;

    public ReportService(BookRepository books, MagazineRepository magazines,
                         NewspaperRepository newspapers, BorrowRecordRepository borrowRecords,
                         StudentProfileRepository students) {
        this.books = books;
        this.magazines = magazines;
        this.newspapers = newspapers;
        this.borrowRecords = borrowRecords;
        this.students = students;
    }

    @Transactional(readOnly = true)
    public String inventoryCsv() {
        var header = csvLine("Type", "ID", "Title", "Author/Publisher", "ISBN", "Date", "Available");
        var bookRows = books.findAll().stream()
                .map(b -> csvLine("Book", b.getId(), b.getTitle(), b.getAuthor(), b.getIsbn(),
                        b.getPublishedDate() != null ? b.getPublishedDate().toString() : "",
                        b.isAvailable() ? "Yes" : "No"));
        var magazineRows = magazines.findAll().stream()
                .map(m -> csvLine("Magazine", m.getId(), m.getTitle(), m.getPublisher(), "",
                        m.getIssueDate() != null ? m.getIssueDate().toString() : "",
                        m.isAvailable() ? "Yes" : "No"));
        var newspaperRows = newspapers.findAll().stream()
                .map(n -> csvLine("Newspaper", n.getId(), n.getTitle(), n.getPublisher(), "",
                        n.getPublicationDate() != null ? n.getPublicationDate().toString() : "",
                        n.isAvailable() ? "Yes" : "No"));
        return Stream.concat(Stream.of(header),
                Stream.concat(bookRows, Stream.concat(magazineRows, newspaperRows)))
                .collect(Collectors.joining("\n"));
    }

    @Transactional(readOnly = true)
    public String borrowingCsv(LocalDate from, LocalDate to) {
        var records = (from != null && to != null)
                ? borrowRecords.findByBorrowDateBetween(from, to, Sort.by(Sort.Direction.DESC, "borrowDate"))
                : borrowRecords.findAll(Sort.by(Sort.Direction.DESC, "borrowDate"));
        var header = csvLine("ID", "Item Title", "Item Type", "Borrower Name", "Borrower Email",
                "Borrower Phone", "Borrow Date", "Return Date", "Status");
        var rows = records.stream().map(r -> {
            var itemType = r.getBook() != null ? "BOOK" : r.getMagazine() != null ? "MAGAZINE" : "NEWSPAPER";
            var itemTitle = r.getBook() != null ? r.getBook().getTitle()
                    : r.getMagazine() != null ? r.getMagazine().getTitle()
                    : r.getNewspaper() != null ? r.getNewspaper().getTitle() : "";
            return csvLine(r.getId(), itemTitle, itemType, r.getBorrowerName(), r.getBorrowerEmail(),
                    r.getBorrowerPhone(), r.getBorrowDate().toString(),
                    r.getReturnDate() != null ? r.getReturnDate().toString() : "",
                    r.getReturnDate() == null ? "BORROWED" : "RETURNED");
        });
        return Stream.concat(Stream.of(header), rows).collect(Collectors.joining("\n"));
    }

    @Transactional(readOnly = true)
    public String studentsCsv() {
        var allBorrows = borrowRecords.findAll();
        var borrowCounts = allBorrows.stream()
                .collect(Collectors.groupingBy(r -> r.getStudent() != null ? r.getStudent().getId() : -1L,
                        Collectors.collectingAndThen(Collectors.toList(), list -> {
                            var total = list.size();
                            var active = list.stream().filter(r -> r.getReturnDate() == null).count();
                            return new long[]{total, active};
                        })));
        var header = csvLine("ID", "Name", "Email", "Phone", "Username", "Total Borrows", "Active Borrows");
        var rows = students.findAll().stream().map(s -> {
            var counts = borrowCounts.getOrDefault(s.getId(), new long[]{0, 0});
            return csvLine(s.getId(), s.getName(), s.getEmail(), s.getPhone(),
                    s.getAccount().getUsername(), counts[0], counts[1]);
        });
        return Stream.concat(Stream.of(header), rows).collect(Collectors.joining("\n"));
    }

    @Transactional(readOnly = true)
    public String overdueCsv() {
        var cutoff = LocalDate.now().minusDays(14);
        var records = borrowRecords.findByReturnDateIsNullAndBorrowDateBefore(cutoff);
        var header = csvLine("ID", "Item Title", "Borrower Name", "Borrow Date", "Days Overdue");
        var rows = records.stream().map(r -> csvLine(r.getId(), itemTitle(r), r.getBorrowerName(),
                r.getBorrowDate().toString(), LocalDate.now().toEpochDay() - r.getBorrowDate().toEpochDay()));
        return Stream.concat(Stream.of(header), rows).collect(Collectors.joining("\n"));
    }

    private String itemTitle(com.example.lms.entity.BorrowRecord r) {
        if (r.getBook() != null) return r.getBook().getTitle();
        if (r.getMagazine() != null) return r.getMagazine().getTitle();
        if (r.getNewspaper() != null) return r.getNewspaper().getTitle();
        return "Unknown";
    }

    private String csvLine(Object... values) {
        return Stream.of(values).map(v -> {
            var s = v != null ? v.toString() : "";
            return s.contains(",") || s.contains("\"") || s.contains("\n")
                    ? "\"" + s.replace("\"", "\"\"") + "\""
                    : s;
        }).collect(Collectors.joining(","));
    }
}
