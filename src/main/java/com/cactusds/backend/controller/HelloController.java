package com.cactusds.backend.controller;
import  org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;



@RestController
public class HelloController {
    @GetMapping("/api/hello")
    public String hello() {
        return "Salam badr, backend is talking to you";
    }
    record Greeting(String message, String from) {}
}
