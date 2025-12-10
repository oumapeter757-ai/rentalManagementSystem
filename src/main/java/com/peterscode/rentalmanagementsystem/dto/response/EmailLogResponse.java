
package com.peterscode.rentalmanagementsystem.dto.response;


import com.peterscode.rentalmanagementsystem.model.logs.EmailStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailLogResponse {
    private Long id;
    private String recipient;
    private String subject;
    private String bodyPreview; // First 100 chars
    private String templateName;
    private EmailStatus status;
    private int retryCount;
    private LocalDateTime sentAt;
    private LocalDateTime lastAttemptAt;
    private String errorMessage;
}