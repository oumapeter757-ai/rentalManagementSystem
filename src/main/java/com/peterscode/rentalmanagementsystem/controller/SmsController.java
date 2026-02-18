package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.service.sms.SmsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sms")
@RequiredArgsConstructor
public class SmsController {

    private final SmsService smsService;

    @PostMapping("/send")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<String>> sendSms(@RequestBody Map<String, String> payload) {
        String to = payload.get("to");
        String message = payload.get("message");

        if (to == null || message == null) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Missing 'to' or 'message' field"));
        }

        smsService.sendSms(to, message);
        return ResponseEntity.ok(ApiResponse.success("SMS sent successfully", null));
    }
}
