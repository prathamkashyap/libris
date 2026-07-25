package com.example.lms.controller;
import com.example.lms.dto.*;
import com.example.lms.service.MagazineService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.net.URI;

@RestController @RequestMapping("/api/magazines")
public class MagazineController {
    private final MagazineService service;
    public MagazineController(MagazineService service){this.service=service;}

    @GetMapping
    public org.springframework.data.domain.Page<MagazineResponse> list(@RequestParam(required=false) String search, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="10") int size){
        return service.list(search, org.springframework.data.domain.PageRequest.of(page, size));
    }

    @GetMapping("/{id}")
    public MagazineResponse get(@PathVariable Long id){return service.get(id);}

    @PostMapping
    public ResponseEntity<MagazineResponse> create(@Valid @RequestBody MagazineRequest request){
        var created = service.create(request);
        return ResponseEntity.created(URI.create("/api/magazines/"+created.id())).body(created);
    }

    @PutMapping("/{id}")
    public MagazineResponse update(@PathVariable Long id, @Valid @RequestBody MagazineRequest request){
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id){service.delete(id);}
}
