package com.example.lms.service;
import com.example.lms.dto.*; import com.example.lms.entity.*; import com.example.lms.event.EntityAuditEvent; import com.example.lms.exception.*; import com.example.lms.repository.*; import com.example.lms.util.CurrentUser; import com.example.lms.util.StringUtils; import org.springframework.context.ApplicationEventPublisher; import org.springframework.security.crypto.password.PasswordEncoder; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.List;
@Service public class LibrarianService {
    private final LibrarianProfileRepository librarians; private final AccountRepository accounts; private final PasswordEncoder passwords; private final ApplicationEventPublisher events; private final CurrentUser currentUser;
    public LibrarianService(LibrarianProfileRepository l,AccountRepository a,PasswordEncoder p,ApplicationEventPublisher events,CurrentUser currentUser){librarians=l;accounts=a;passwords=p;this.events=events;this.currentUser=currentUser;}
    @Transactional(readOnly=true) public org.springframework.data.domain.Page<LibrarianResponse> list(String search, org.springframework.data.domain.Pageable pageable){var source=search==null||search.isBlank()?librarians.findAll(pageable):librarians.search(search,pageable);return source.map(this::response);}
    @Transactional(readOnly=true) public LibrarianResponse get(Long id){return response(librarian(id));}
    @Transactional public LibrarianResponse create(LibrarianRequest r){
        if(accounts.existsByUsername(r.username()))throw new ConflictException("Username is already in use.");
        var a=new Account();a.setUsername(r.username().trim());a.setPasswordHash(passwords.encode(r.password()));a.setRole(Role.LIBRARIAN);
        var p=new LibrarianProfile();p.setAccount(accounts.save(a));apply(p,r);var saved=response(librarians.save(p));
        var actor=currentUser.get();events.publishEvent(new EntityAuditEvent(this,AuditAction.CREATE,AuditEntityType.LIBRARIAN,saved.id(),"Librarian created: "+saved.name(),actor.id(),actor.username(),actor.role(),actor.ipAddress(),actor.userAgent()));
        return saved;
    }
    @Transactional public LibrarianResponse update(Long id,LibrarianUpdateRequest r){
        var p=librarian(id);if(!p.getAccount().getUsername().equals(r.username())&&accounts.existsByUsername(r.username()))throw new ConflictException("Username is already in use.");
        p.getAccount().setUsername(r.username().trim());apply(p,r);var saved=response(librarians.save(p));
        var actor=currentUser.get();events.publishEvent(new EntityAuditEvent(this,AuditAction.UPDATE,AuditEntityType.LIBRARIAN,id,"Librarian updated: "+saved.name(),actor.id(),actor.username(),actor.role(),actor.ipAddress(),actor.userAgent()));
        return saved;
    }
    @Transactional public void delete(Long id){var p=librarian(id);var name=p.getName();librarians.delete(p);
        var actor=currentUser.get();events.publishEvent(new EntityAuditEvent(this,AuditAction.DELETE,AuditEntityType.LIBRARIAN,id,"Librarian deleted: "+name,actor.id(),actor.username(),actor.role(),actor.ipAddress(),actor.userAgent()));
    }
    private LibrarianProfile librarian(Long id){return librarians.findById(id).orElseThrow(()->new ResourceNotFoundException("Librarian %d was not found.".formatted(id)));}
    private void apply(LibrarianProfile p,LibrarianRequest r){p.setName(r.name().trim());p.setAge(r.age());p.setPhone(StringUtils.blankToNull(r.phone()));}
    private void apply(LibrarianProfile p,LibrarianUpdateRequest r){apply(p, new LibrarianRequest(r.username(), null, r.name(), r.age(), r.phone()));}
    private LibrarianResponse response(LibrarianProfile p){var a=p.getAccount();return new LibrarianResponse(p.getId(),a.getId(),a.getUsername(),p.getName(),p.getAge(),p.getPhone(),a.getRole().name());}
}
