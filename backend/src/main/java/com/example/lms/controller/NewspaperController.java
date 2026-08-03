package com.example.lms.controller;
import com.example.lms.dto.*;
import com.example.lms.service.NewspaperService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController @RequestMapping("/api/newspapers")
public class NewspaperController {
    private final NewspaperService service;
    public NewspaperController(NewspaperService service){this.service=service;}

    @GetMapping
    public org.springframework.data.domain.Page<NewspaperResponse> list(@RequestParam(required=false) String search, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size){
        return service.list(search, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public NewspaperResponse get(@PathVariable Long id){return service.get(id);}

    @PostMapping
    public ResponseEntity<NewspaperResponse> create(@Valid @RequestBody NewspaperRequest request){
        var created = service.create(request);
        return ResponseEntity.created(URI.create("/api/newspapers/"+created.id())).body(created);
    }

    @PutMapping("/{id}")
    public NewspaperResponse update(@PathVariable Long id, @Valid @RequestBody NewspaperRequest request){
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id){service.delete(id);}
}
