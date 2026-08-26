package com.example.lms.controller;

import com.example.lms.dto.*;
import com.example.lms.service.StudentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/students")
@Tag(name = "Students", description = "Student profile management")
public class StudentController {
  private final StudentService service;

  public StudentController(StudentService s) {
    service = s;
  }

  @GetMapping
  @Operation(
      summary = "List students",
      description = "Paginated list with optional search by name, email, or username.")
  public org.springframework.data.domain.Page<StudentResponse> list(
      @RequestParam(required = false) String query,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(query, org.springframework.data.domain.PageRequest.of(page, size));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get student by ID")
  public StudentResponse get(@PathVariable Long id) {
    return service.get(id);
  }

  @PostMapping
  @Operation(summary = "Create a student")
  @ApiResponse(responseCode = "201", description = "Student created")
  public ResponseEntity<StudentResponse> create(@Valid @RequestBody StudentRequest r) {
    var p = service.create(r);
    return ResponseEntity.created(URI.create("/api/students/" + p.id())).body(p);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update a student")
  public StudentResponse update(@PathVariable Long id, @Valid @RequestBody StudentUpdateRequest r) {
    return service.update(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a student")
  @ApiResponse(responseCode = "204", description = "Student deleted")
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
