package com.example.lms.controller;

import com.example.lms.dto.*;
import com.example.lms.service.MagazineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/magazines")
@Tag(name = "Magazines", description = "Catalogue management for magazines")
public class MagazineController {
  private final MagazineService service;

  public MagazineController(MagazineService service) {
    this.service = service;
  }

  @GetMapping
  @Operation(
      summary = "List magazines",
      description = "Paginated list with optional search by title or publisher.")
  public org.springframework.data.domain.Page<MagazineResponse> list(
      @RequestParam(required = false) String search,
      @RequestParam(required = false) String query,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "10") int size) {
    var q = query != null ? query : search;
    return service.list(q, org.springframework.data.domain.PageRequest.of(page, size));
  }

  @GetMapping("/{id}")
  @Operation(summary = "Get magazine by ID")
  public MagazineResponse get(@PathVariable Long id) {
    return service.get(id);
  }

  @PostMapping
  @Operation(summary = "Create a magazine")
  @ApiResponse(responseCode = "201", description = "Magazine created")
  public ResponseEntity<MagazineResponse> create(@Valid @RequestBody MagazineRequest request) {
    var created = service.create(request);
    return ResponseEntity.created(URI.create("/api/magazines/" + created.id())).body(created);
  }

  @PutMapping("/{id}")
  @Operation(summary = "Update a magazine")
  public MagazineResponse update(
      @PathVariable Long id, @Valid @RequestBody MagazineRequest request) {
    return service.update(id, request);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @Operation(summary = "Delete a magazine")
  @ApiResponse(responseCode = "204", description = "Magazine deleted")
  public void delete(@PathVariable Long id) {
    service.delete(id);
  }
}
