package com.example.lms.exception;
import com.example.lms.dto.ApiErrorResponse; import jakarta.servlet.http.HttpServletRequest; import org.springframework.http.*; import org.springframework.validation.FieldError; import org.springframework.web.bind.MethodArgumentNotValidException; import org.springframework.web.bind.annotation.*; import java.time.Instant; import java.util.List;
@RestControllerAdvice
public class GlobalExceptionHandler {
 @ExceptionHandler(ResourceNotFoundException.class) @ResponseStatus(HttpStatus.NOT_FOUND) ApiErrorResponse notFound(ResourceNotFoundException e,HttpServletRequest r){return error(404,"NOT_FOUND",e.getMessage(),r,List.of());}
 @ExceptionHandler(ConflictException.class) @ResponseStatus(HttpStatus.CONFLICT) ApiErrorResponse conflict(ConflictException e,HttpServletRequest r){return error(409,"CONFLICT",e.getMessage(),r,List.of());}
 @ExceptionHandler(BusinessRuleException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) ApiErrorResponse business(BusinessRuleException e,HttpServletRequest r){return error(400,e.getCode(),e.getMessage(),r,List.of());}
 @ExceptionHandler(MethodArgumentNotValidException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) ApiErrorResponse invalid(MethodArgumentNotValidException e,HttpServletRequest r){var fields=e.getBindingResult().getFieldErrors().stream().map(f->new ApiErrorResponse.FieldError(f.getField(),f.getDefaultMessage())).toList();return error(400,"VALIDATION_ERROR","Request validation failed.",r,fields);}
 private ApiErrorResponse error(int status,String code,String message,HttpServletRequest r,List<ApiErrorResponse.FieldError> fields){return new ApiErrorResponse(Instant.now(),status,code,message,r.getRequestURI(),fields);}
}
