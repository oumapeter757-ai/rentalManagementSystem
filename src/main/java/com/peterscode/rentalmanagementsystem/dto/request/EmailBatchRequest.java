
package com.peterscode.rentalmanagementsystem.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailBatchRequest {
    private List<String> recipients;
    private String subject;
    private String body;
    private String templateName;
}