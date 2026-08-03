package com.example.lms.service;

import com.example.lms.dto.*;
import com.example.lms.entity.*;
import com.example.lms.exception.*;
import com.example.lms.repository.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;

@Service
public class BorrowRecordService {
    private final BorrowRecordRepository records;
    private final BookRepository books;
    private final MagazineRepository magazines;
    private final NewspaperRepository newspapers;
    private final StudentProfileRepository students;

    public BorrowRecordService(BorrowRecordRepository r, BookRepository b, MagazineRepository m, NewspaperRepository n, StudentProfileRepository s) {
        records = r; books = b; magazines = m; newspapers = n; students = s;
    }

    @Transactional(readOnly = true)
    public Page<BorrowRecordResponse> list(String status, Pageable pageable) {
        Page<BorrowRecord> source = "BORROWED".equalsIgnoreCase(status) ? records.findByReturnDateIsNull(pageable)
                : "RETURNED".equalsIgnoreCase(status) ? records.findByReturnDateIsNotNull(pageable)
                : records.findAll(pageable);
        return source.map(this::response);
    }

    @Transactional(readOnly = true)
    public Page<BorrowRecordResponse> listByStudentId(Long studentId, String status, Pageable pageable) {
        Page<BorrowRecord> source = "BORROWED".equalsIgnoreCase(status) ? records.findByStudentIdAndReturnDateIsNull(studentId, pageable)
                : "RETURNED".equalsIgnoreCase(status) ? records.findByStudentIdAndReturnDateIsNotNull(studentId, pageable)
                : records.findByStudentId(studentId, pageable);
        return source.map(this::response);
    }

    @Transactional
    public BorrowRecordResponse borrow(BorrowRequest r) {
        var student = students.findById(r.studentId()).orElseThrow(() -> new ResourceNotFoundException("Student not found."));
        var record = new BorrowRecord();
        record.setStudent(student);
        record.setBorrowerName(student.getName());
        record.setBorrowerEmail(student.getEmail());
        record.setBorrowerPhone(student.getPhone());
        record.setBorrowDate(r.borrowDate());

        if (r.bookId() != null) {
            var book = books.findById(r.bookId()).orElseThrow(() -> new ResourceNotFoundException("Book not found."));
            if (!book.isAvailable()) throw new BusinessRuleException("UNAVAILABLE", "Book is not available.");
            book.setAvailable(false);
            record.setBook(book);
        } else if (r.magazineId() != null) {
            var magazine = magazines.findById(r.magazineId()).orElseThrow(() -> new ResourceNotFoundException("Magazine not found."));
            if (!magazine.isAvailable()) throw new BusinessRuleException("UNAVAILABLE", "Magazine is not available.");
            magazine.setAvailable(false);
            record.setMagazine(magazine);
        } else if (r.newspaperId() != null) {
            var newspaper = newspapers.findById(r.newspaperId()).orElseThrow(() -> new ResourceNotFoundException("Newspaper not found."));
            if (!newspaper.isAvailable()) throw new BusinessRuleException("UNAVAILABLE", "Newspaper is not available.");
            newspaper.setAvailable(false);
            record.setNewspaper(newspaper);
        } else {
            throw new BusinessRuleException("INVALID_REQUEST", "Must specify an item to borrow.");
        }

        return response(records.save(record));
    }

    @Transactional
    public void returnBook(Long id) {
        var record = records.findById(id).orElseThrow(() -> new ResourceNotFoundException("Borrow record not found."));
        if (record.getReturnDate() != null) throw new BusinessRuleException("ALREADY_RETURNED", "Already returned.");
        
        record.setReturnDate(LocalDate.now());
        if (record.getBook() != null) record.getBook().setAvailable(true);
        if (record.getMagazine() != null) record.getMagazine().setAvailable(true);
        if (record.getNewspaper() != null) record.getNewspaper().setAvailable(true);
    }

    private BorrowRecordResponse response(BorrowRecord r) {
        Long itemId = null; String itemTitle = null; String itemType = null;
        if (r.getBook() != null) { itemId = r.getBook().getId(); itemTitle = r.getBook().getTitle(); itemType = "BOOK"; }
        else if (r.getMagazine() != null) { itemId = r.getMagazine().getId(); itemTitle = r.getMagazine().getTitle(); itemType = "MAGAZINE"; }
        else if (r.getNewspaper() != null) { itemId = r.getNewspaper().getId(); itemTitle = r.getNewspaper().getTitle(); itemType = "NEWSPAPER"; }
        
        return new BorrowRecordResponse(r.getId(), itemId, itemTitle, itemType, r.getStudent().getId(),
                r.getBorrowerName(), r.getBorrowerEmail(), r.getBorrowerPhone(),
                r.getBorrowDate(), r.getReturnDate(), r.getReturnDate() == null ? "BORROWED" : "RETURNED");
    }
}
