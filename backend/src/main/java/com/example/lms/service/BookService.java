package com.example.lms.service;
import com.example.lms.dto.*; import com.example.lms.entity.Book; import com.example.lms.exception.*; import com.example.lms.repository.*; import org.springframework.dao.DataIntegrityViolationException; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.util.List;
@Service
public class BookService {
 private final BookRepository books; private final BorrowRecordRepository records;
 public BookService(BookRepository books,BorrowRecordRepository records){this.books=books;this.records=records;}
 @Transactional(readOnly=true) public List<BookResponse> list(String search){var source=search==null||search.isBlank()?books.findAll():books.findByTitleContainingIgnoreCaseOrAuthorContainingIgnoreCase(search,search);return source.stream().map(this::response).toList();}
 @Transactional(readOnly=true) public BookResponse get(Long id){return response(book(id));}
 @Transactional public BookResponse create(BookRequest request){var entity=new Book();apply(entity,request);validateUniqueIsbn(entity.getIsbn(),null);entity.setAvailable(true);return save(entity);}
 @Transactional public BookResponse update(Long id,BookRequest request){var entity=book(id);apply(entity,request);validateUniqueIsbn(entity.getIsbn(),id);return save(entity);}
 @Transactional public void delete(Long id){var entity=book(id);if(records.existsByBookId(id))throw new ConflictException("A book with borrow history cannot be deleted.");books.delete(entity);}
 private Book book(Long id){return books.findById(id).orElseThrow(()->new ResourceNotFoundException("Book %d was not found.".formatted(id)));}
 private void validateUniqueIsbn(String isbn,Long currentBookId){if(isbn!=null&&(currentBookId==null?books.existsByIsbn(isbn):books.existsByIsbnAndIdNot(isbn,currentBookId)))throw new ConflictException("ISBN already exists.");}
 private BookResponse save(Book book){try{return response(books.saveAndFlush(book));}catch(DataIntegrityViolationException e){throw new ConflictException("ISBN already exists.");}}
 private void apply(Book entity,BookRequest r){entity.setTitle(r.title().trim());entity.setAuthor(blankToNull(r.author()));entity.setIsbn(blankToNull(r.isbn()));entity.setPublishedDate(r.publishedDate());}
 private String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
 private BookResponse response(Book b){return new BookResponse(b.getId(),b.getTitle(),b.getAuthor(),b.getIsbn(),b.getPublishedDate(),b.isAvailable());}
}
