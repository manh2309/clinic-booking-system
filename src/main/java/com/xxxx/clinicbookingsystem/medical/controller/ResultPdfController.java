package com.xxxx.clinicbookingsystem.medical.controller;

import com.xxxx.clinicbookingsystem.medical.service.ResultPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.io.IOException;

@RestController @RequiredArgsConstructor
public class ResultPdfController {
    private final ResultPdfService service;
    @GetMapping("/api/v1/patient/me/medical-records/{id}/pdf") @PreAuthorize("hasRole('PATIENT')")
    public ResponseEntity<byte[]> patient(@PathVariable Long id)throws IOException{return result(id,false);}
    @GetMapping("/api/v1/doctors/me/appointments/{id}/medical-record/pdf") @PreAuthorize("hasRole('DOCTOR')")
    public ResponseEntity<byte[]> doctor(@PathVariable Long id)throws IOException{return result(id,true);}
    private ResponseEntity<byte[]> result(Long id,boolean doctor)throws IOException {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION,"inline; filename=\"ket-qua-kham-"+id+".pdf\"")
                .body(service.create(id,doctor));
    }
}
