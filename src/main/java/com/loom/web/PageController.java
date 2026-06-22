package com.loom.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/chat")
    public String chat() {
        return "fragments/chat :: page";
    }

    @GetMapping("/graph")
    public String graph() {
        return "fragments/graph :: page";
    }

    @GetMapping("/notes")
    public String notes() {
        return "fragments/notes :: page";
    }
}
