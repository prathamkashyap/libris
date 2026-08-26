package com.example.lms.controller;

import com.example.lms.dto.*;
import com.example.lms.service.LibrarianService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/librarians")
@Tag(name = "Librarians", description = "Librarian profile management (admin only)")
public class LibrarianController {
  private final LibrarianService service;

  public LibrarianController(LibrarianService s) {
    service = s;
  }

  @GetMapping
  @Operation(
      summary = "List librarians",
      description = "Paginated list with optional search by name or username.")
  public org.springframework.data.domain.Page<LibrarianResponse> list(
      @RequestParam(required = false) String query,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    return service.list(query, org.springframework.data.domain.PageRequest.of(page, size));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get librarian by ID")
  public LibrarianResponse get(@PathVariable Long id) {
    return service.get(id);
  }

  @PostMapping
  @Operation(summary = "Create a librarian")
  @ApiResponse(responseCode = "201", description = "Librarian created")
  public ResponseEntity<LibrarianResponse> create(@Valid @RequestBody LibrarianRequest r) {
    var p = service.create(r);
    return ResponseEntity.created(URI.create("/api/librarians/" + p.id())).body(p);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update a librarian")
  public LibrarianResponse update(
      @PathVariable Long id, @Valid @RequestBody LibrarianUpdateRequest r) {
    return service.update(id, r);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a librarian")
  @ApiResponse(responseCode = "204", description = "Librarian deleted")
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
