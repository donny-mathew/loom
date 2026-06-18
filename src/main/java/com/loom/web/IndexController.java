package com.loom.web;

import com.loom.index.IndexRebuildService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/index")
public class IndexController {

    private final IndexRebuildService rebuildService;

    public IndexController(IndexRebuildService rebuildService) {
        this.rebuildService = rebuildService;
    }

    @PostMapping("/rebuild")
    public ResponseEntity<String> rebuild() {
        rebuildService.rebuild();
        return ResponseEntity.ok("Index rebuilt successfully.");
    }
}
