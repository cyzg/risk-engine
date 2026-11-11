package com.example.risk.api;

import com.example.risk.rule.RuleConfig;
import com.example.risk.rule.RuleEngine;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Hot-update rule parameters without restart. TODO: secure with auth and persist to PostgreSQL. */
@RestController
@RequestMapping("/admin/rules")
public class RuleAdminController {
    private final RuleEngine engine;

    public RuleAdminController(RuleEngine engine) {
        this.engine = engine;
    }

    @GetMapping("/version")
    public Map<String, Long> version() {
        return Map.of("version", engine.version());
    }

    @PutMapping
    public Map<String, Long> update(@RequestBody RuleConfig config) {
        return Map.of("version", engine.reload(config));
    }
}
