package com.scantaker.api.controller;

import com.scantaker.api.dto.BookingDtos.*;
import com.scantaker.api.service.BookingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/bookings")
@RequiredArgsConstructor
public class BookingController {
    private final BookingService service;

    // login ke bina bhi chalega. Login hai to booking user se jud jayegi
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public View create(@Valid @RequestBody Create r, @AuthenticationPrincipal Long userId) {
        return service.create(r, userId);
    }

    @GetMapping("/mine")
    public List<View> mine(@AuthenticationPrincipal Long userId) { return service.mine(userId); }

    @GetMapping("/{code}")
    public View one(@PathVariable String code, @AuthenticationPrincipal Long userId) {
        return service.get(code, userId);
    }

    @PostMapping("/{code}/cancel")
    public View cancel(@PathVariable String code, @AuthenticationPrincipal Long userId) {
        return service.cancel(code, userId);
    }

    @PostMapping("/{code}/feedback")
    public View feedback(@PathVariable String code, @RequestBody FeedbackRequest r, @AuthenticationPrincipal Long userId) {
        return service.submitFeedback(code, r.rating(), r.feedback(), userId);
    }
        @PostMapping(value = "/{code}/prescription", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public View upload(@PathVariable String code, @RequestParam("file") MultipartFile file,
                       @RequestParam(required = false) String mobile, @AuthenticationPrincipal Long userId) {
        return service.attachPrescription(code, file, mobile, userId);
    }

    @GetMapping("/{code}/prescription")
    public ResponseEntity<Resource> prescription(@PathVariable String code, @AuthenticationPrincipal Long userId) {
        var r = service.prescription(code, userId, false);
        return ResponseEntity.ok().contentType(r.type())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + r.name() + "\"")
                .header("X-Content-Type-Options", "nosniff").body(r.file());
    }

    @GetMapping("/{code}/report")
    public ResponseEntity<Resource> report(@PathVariable String code, @AuthenticationPrincipal Long userId) {
        var r = service.report(code, userId, false);
        return ResponseEntity.ok().contentType(r.type())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + r.name() + "\"")
                .header("X-Content-Type-Options", "nosniff").body(r.file());
    }
}