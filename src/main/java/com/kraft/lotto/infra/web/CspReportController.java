package com.kraft.lotto.infra.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/csp")
public class CspReportController {

    @PostMapping("/report")
    public ResponseEntity<Void> collectReport(@RequestBody(required = false) String body) {
        return ResponseEntity.noContent().build();
    }
}
