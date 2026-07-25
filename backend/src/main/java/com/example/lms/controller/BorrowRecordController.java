package com.example.lms.controller;
import com.example.lms.dto.*; import com.example.lms.service.BorrowRecordService; import com.example.lms.repository.StudentProfileRepository; import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.security.core.Authentication; import org.springframework.web.bind.annotation.*; import java.net.URI;
@RestController @RequestMapping("/api/borrow-records") public class BorrowRecordController {private final BorrowRecordService service; private final StudentProfileRepository studentRepo; public BorrowRecordController(BorrowRecordService s, StudentProfileRepository sr){service=s; studentRepo=sr;}
@GetMapping public org.springframework.data.domain.Page<BorrowRecordResponse> list(@RequestParam(required=false) String status, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size){return service.list(status, org.springframework.data.domain.PageRequest.of(page, size));}
@GetMapping("/my") public org.springframework.data.domain.Page<BorrowRecordResponse> myRecords(@RequestParam(required=false) String status, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size, Authentication auth){
    Long studentId = studentRepo.findByAccountUsername(auth.getName())
            .orElseThrow(() -> new com.example.lms.exception.ResourceNotFoundException("Student not found."))
            .getId();
    return service.listByStudentId(studentId, status, org.springframework.data.domain.PageRequest.of(page, size));
}
@PostMapping public ResponseEntity<BorrowRecordResponse> borrow(@Valid @RequestBody BorrowRequest r){var created=service.borrow(r);return ResponseEntity.created(URI.create("/api/borrow-records/"+created.id())).body(created);}
@PostMapping("/{id}/return") @ResponseStatus(HttpStatus.NO_CONTENT) public void returnBook(@PathVariable Long id){service.returnBook(id);}}
