package com.scantaker.api.controller;

import com.scantaker.api.dto.BookingDtos.*;
import com.scantaker.api.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/admin/bookings")
@RequiredArgsConstructor
public class AdminBookingController {
    private final BookingService service;

    @GetMapping
    public List<View> list(@RequestParam(required = false) String status) { return service.adminList(status); }

    @PutMapping("/{code}")
    public View update(@PathVariable String code, @RequestBody AdminUpdate u) { return service.adminUpdate(code, u); }

    @DeleteMapping("/{code}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String code) {
        service.deleteBooking(code);
    }

    @GetMapping("/{code}/prescription")
    public ResponseEntity<Resource> prescription(@PathVariable String code) {
        var r = service.prescription(code, null, true);
        return ResponseEntity.ok().contentType(r.type())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + r.name() + "\"")
                .header("X-Content-Type-Options", "nosniff").body(r.file());
    }

    @PostMapping(value = "/{code}/report", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public View uploadReport(@PathVariable String code, @RequestParam("file") MultipartFile file) {
        return service.attachReport(code, file);
    }

    @GetMapping("/{code}/report")
    public ResponseEntity<Resource> report(@PathVariable String code) {
        var r = service.report(code, null, true);
        return ResponseEntity.ok().contentType(r.type())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + r.name() + "\"")
                .header("X-Content-Type-Options", "nosniff").body(r.file());
    }
}