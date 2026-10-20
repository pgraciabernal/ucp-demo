package com.ucp.poc.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PoppyDiscoveryController {

    @GetMapping(value = "/.well-known/poppy.json", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Resource> getPoppyManifest() {
        Resource resource = new ClassPathResource("static/.well-known/poppy.json");
        if (!resource.exists()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(resource);
    }
}

