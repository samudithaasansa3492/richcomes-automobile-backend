package com.richcomes.garage.auth.recovery;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
@RestControllerAdvice(assignableTypes = RecoveryController.class)
public class RecoveryExceptionHandler {
    @ExceptionHandler({RecoveryFailure.class, MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String,String>> invalid(Exception exception) {
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore())
            .body(Map.of("code", "RECOVERY_UNAVAILABLE", "message", "Unable to complete recovery request"));
    }
}
