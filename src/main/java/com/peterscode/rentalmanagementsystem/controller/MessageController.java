package com.peterscode.rentalmanagementsystem.controller;

import com.peterscode.rentalmanagementsystem.dto.response.ApiResponse;
import com.peterscode.rentalmanagementsystem.model.audit.AuditAction;
import com.peterscode.rentalmanagementsystem.model.audit.EntityType;
import com.peterscode.rentalmanagementsystem.security.SecurityUser;
import com.peterscode.rentalmanagementsystem.service.audit.AuditLogService;
import com.peterscode.rentalmanagementsystem.service.message.MessageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
@Tag(name = "Messaging", description = "APIs for user-to-user messaging")
public class MessageController {

    private final MessageService messageService;
    private final AuditLogService auditLogService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Send a message")
    public ResponseEntity<ApiResponse<Map<String, Object>>> sendMessage(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {
        Long senderId = getUserId(authentication);
        Long receiverId = Long.valueOf(body.get("receiverId").toString());
        String content = body.get("content").toString();

        Map<String, Object> message = messageService.sendMessage(senderId, receiverId, content);

        auditLogService.log(AuditAction.SEND_MESSAGE, EntityType.MESSAGE,
                (Long) message.get("id"),
                String.format("Message sent to user ID: %d", receiverId));

        return ResponseEntity.ok(ApiResponse.success("Message sent", message));
    }

    @GetMapping("/conversations")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get list of conversations")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getConversations(
            Authentication authentication) {
        Long userId = getUserId(authentication);
        List<Map<String, Object>> conversations = messageService.getConversationList(userId);
        return ResponseEntity.ok(ApiResponse.success("Conversations retrieved", conversations));
    }

    @GetMapping("/conversation/{otherUserId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get messages in a conversation")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getConversation(
            @PathVariable Long otherUserId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        List<Map<String, Object>> messages = messageService.getConversation(userId, otherUserId);

        auditLogService.log(AuditAction.VIEW, EntityType.MESSAGE, null,
                String.format("Viewed conversation with user ID: %d", otherUserId));

        return ResponseEntity.ok(ApiResponse.success("Conversation retrieved", messages));
    }

    @PutMapping("/{messageId}/read")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Mark a message as read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @PathVariable Long messageId,
            Authentication authentication) {
        Long userId = getUserId(authentication);
        messageService.markAsRead(messageId, userId);
        return ResponseEntity.ok(ApiResponse.success("Message marked as read", null));
    }

    @GetMapping("/unread-count")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get unread message count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(Authentication authentication) {
        Long userId = getUserId(authentication);
        long count = messageService.getUnreadCount(userId);
        return ResponseEntity.ok(ApiResponse.success("Unread count", count));
    }

    @GetMapping("/contacts")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get available contacts to message")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getContacts(
            Authentication authentication) {
        Long userId = getUserId(authentication);
        List<Map<String, Object>> contacts = messageService.getAvailableContacts(userId);
        return ResponseEntity.ok(ApiResponse.success("Contacts retrieved", contacts));
    }

    private Long getUserId(Authentication authentication) {
        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        return securityUser.user().getId();
    }
}
