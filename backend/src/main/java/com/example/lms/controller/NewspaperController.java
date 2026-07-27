package com.example.lms.controller;
import com.example.lms.dto.*;
import com.example.lms.service.NewspaperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController @RequestMapping("/api/newspapers") @Tag(name="Newspapers",description="Catalogue management for newspapers")
public class NewspaperController {
    private final NewspaperService service;
    public NewspaperController(NewspaperService service){this.service=service;}

    @GetMapping @Operation(summary="List newspapers",description="Paginated list with optional search by title or publisher.")
    public org.springframework.data.domain.Page<NewspaperResponse> list(@RequestParam(required=false) String search,@RequestParam(required=false) String query, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size){
        var q=query!=null?query:search;return service.list(q, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @GetMapping("/{id}") @Operation(summary="Get newspaper by ID")
    public NewspaperResponse get(@PathVariable Long id){return service.get(id);}

    @PostMapping @Operation(summary="Create a newspaper") @ApiResponse(responseCode="201",description="Newspaper created")
    public ResponseEntity<NewspaperResponse> create(@Valid @RequestBody NewspaperRequest request){
        var created = service.create(request);
        return ResponseEntity.created(URI.create("/api/newspapers/"+created.id())).body(created);
    }

    @PutMapping("/{id}") @Operation(summary="Update a newspaper")
    public NewspaperResponse update(@PathVariable Long id, @Valid @RequestBody NewspaperRequest request){
        return service.update(id, request);
    }

    @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) @Operation(summary="Delete a newspaper") @ApiResponse(responseCode="204",description="Newspaper deleted")
    public void delete(@PathVariable Long id){service.delete(id);}
}
