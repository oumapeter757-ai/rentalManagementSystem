package com.peterscode.rentalmanagementsystem.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailBatchRequest {
    private List<String> recipients;
    private String subject;
    private String body;
    private String templateName;

    @Builder.Default
    private boolean html = true;

    private Map<String, Object> variables;
}