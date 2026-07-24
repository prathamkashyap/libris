package com.example.lms.controller;
import com.example.lms.dto.*; import com.example.lms.service.BookService; import jakarta.validation.Valid; import org.springframework.http.*; import org.springframework.web.bind.annotation.*; import java.net.URI; import java.util.List;
@RestController @RequestMapping("/api/books")
public class BookController { private final BookService service; public BookController(BookService service){this.service=service;}
 @GetMapping public List<BookResponse> list(@RequestParam(required=false) String search){return service.list(search);}
 @GetMapping("/{id}") public BookResponse get(@PathVariable Long id){return service.get(id);}
 @PostMapping public ResponseEntity<BookResponse> create(@Valid @RequestBody BookRequest request){var created=service.create(request);return ResponseEntity.created(URI.create("/api/books/"+created.id())).body(created);}
 @PutMapping("/{id}") public BookResponse update(@PathVariable Long id,@Valid @RequestBody BookRequest request){return service.update(id,request);}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id){service.delete(id);}
}
