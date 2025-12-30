package com.transactionapi.controller;

import com.transactionapi.constants.ApiPaths;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

    @GetMapping({ApiPaths.HEALTH, "/"})
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
