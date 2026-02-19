package com.peterscode.rentalmanagementsystem.service.message;

import com.peterscode.rentalmanagementsystem.exception.ResourceNotFoundException;
import com.peterscode.rentalmanagementsystem.model.message.ConversationType;
import com.peterscode.rentalmanagementsystem.model.message.Message;
import com.peterscode.rentalmanagementsystem.model.user.Role;
import com.peterscode.rentalmanagementsystem.model.user.User;
import com.peterscode.rentalmanagementsystem.repository.MessageRepository;
import com.peterscode.rentalmanagementsystem.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    /**
     * Send a message from one user to another.
     * Automatically determines conversation type based on roles.
     * Enforces security: Tenants can only message Landlords, Landlords can message Tenants/Admins, Admins can message Landlords
     */
    public Map<String, Object> sendMessage(Long senderId, Long receiverId, String content) {
        User sender = userRepository.findById(senderId)
                .orElseThrow(() -> new ResourceNotFoundException("Sender not found"));
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ResourceNotFoundException("Receiver not found"));

        // Enforce messaging rules
        validateMessagingPermissions(sender, receiver);

        ConversationType type = determineConversationType(sender, receiver);

        Message message = Message.builder()
                .sender(sender)
                .receiver(receiver)
                .content(content)
                .conversationType(type)
                .sentAt(Instant.now())
                .build();

        Message saved = messageRepository.save(message);
        log.info("Message sent from {} to {} (type: {})", sender.getEmail(), receiver.getEmail(), type);
        return toMessageMap(saved);
    }

    /**
     * Validate that the sender is allowed to message the receiver
     */
    private void validateMessagingPermissions(User sender, User receiver) {
        // Tenants can only message Landlords
        if (sender.getRole() == Role.TENANT) {
            if (receiver.getRole() != Role.LANDLORD) {
                throw new IllegalArgumentException("Tenants can only send messages to Landlords");
            }
        }

        // Landlords can message Tenants and Admins
        else if (sender.getRole() == Role.LANDLORD) {
            if (receiver.getRole() != Role.TENANT && receiver.getRole() != Role.ADMIN) {
                throw new IllegalArgumentException("Landlords can only send messages to Tenants or Admins");
            }
        }

        // Admins can message Landlords
        else if (sender.getRole() == Role.ADMIN) {
            if (receiver.getRole() != Role.LANDLORD) {
                throw new IllegalArgumentException("Admins can only send messages to Landlords");
            }
        }

        else {
            throw new IllegalArgumentException("Invalid user role for messaging");
        }
    }

    /**
     * Get conversation between current user and another user.
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConversation(Long userId, Long otherUserId) {
        List<Message> messages = messageRepository.findConversation(userId, otherUserId);

        // Mark unread messages as read
        messages.stream()
                .filter(m -> m.getReceiver().getId().equals(userId) && m.getReadAt() == null)
                .forEach(m -> {
                    m.setReadAt(Instant.now());
                    messageRepository.save(m);
                });

        return messages.stream().map(this::toMessageMap).collect(Collectors.toList());
    }

    /**
     * Get list of conversations for a user (all unique chat partners with last
     * message).
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getConversationList(Long userId) {
        List<Long> partnerIds = messageRepository.findConversationPartnerIds(userId);

        List<Map<String, Object>> conversations = new ArrayList<>();
        for (Long partnerId : partnerIds) {
            User partner = userRepository.findById(partnerId).orElse(null);
            if (partner == null)
                continue;

            Message lastMessage = messageRepository.findLatestMessage(userId, partnerId);
            long unread = messageRepository.countUnreadFromSender(partnerId, userId);

            Map<String, Object> conv = new LinkedHashMap<>();
            conv.put("partnerId", partner.getId());
            conv.put("partnerName", (partner.getFirstName() != null ? partner.getFirstName() : "") + " " +
                    (partner.getLastName() != null ? partner.getLastName() : ""));
            conv.put("partnerEmail", partner.getEmail());
            conv.put("partnerRole", partner.getRole().name());
            conv.put("lastMessage", lastMessage != null ? lastMessage.getContent() : "");
            conv.put("lastMessageAt", lastMessage != null ? lastMessage.getSentAt().toString() : "");
            conv.put("unreadCount", unread);
            conversations.add(conv);
        }

        // Sort by last message time (most recent first)
        conversations.sort((a, b) -> {
            String timeA = (String) a.get("lastMessageAt");
            String timeB = (String) b.get("lastMessageAt");
            return timeB.compareTo(timeA);
        });

        return conversations;
    }

    /**
     * Mark a message as read.
     */
    public void markAsRead(Long messageId, Long userId) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Message not found"));

        if (message.getReceiver().getId().equals(userId) && message.getReadAt() == null) {
            message.setReadAt(Instant.now());
            messageRepository.save(message);
        }
    }

    /**
     * Get unread message count for a user.
     */
    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return messageRepository.countUnreadByReceiverId(userId);
    }

    /**
     * Get all users that the current user is allowed to message.
     * - Tenants can message Landlords
     * - Landlords can message Tenants and Admins
     * - Admins can message Landlords
     */
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getAvailableContacts(Long userId) {
        User currentUser = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        List<User> contacts;
        if (currentUser.getRole() == Role.TENANT) {
            contacts = userRepository.findByRole(Role.LANDLORD);
        } else if (currentUser.getRole() == Role.LANDLORD) {
            contacts = userRepository.findByRole(Role.ADMIN);
            contacts.addAll(userRepository.findByRole(Role.TENANT));
        } else if (currentUser.getRole() == Role.ADMIN) {
            contacts = userRepository.findByRole(Role.LANDLORD);
        } else {
            contacts = Collections.emptyList();
        }

        return contacts.stream().map(u -> {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("id", u.getId());
            map.put("name", (u.getFirstName() != null ? u.getFirstName() : "") + " " +
                    (u.getLastName() != null ? u.getLastName() : ""));
            map.put("email", u.getEmail());
            map.put("role", u.getRole().name());
            return map;
        }).collect(Collectors.toList());
    }

    // --- Helpers ---

    private ConversationType determineConversationType(User sender, User receiver) {
        boolean isTenantLandlord = (sender.getRole() == Role.TENANT && receiver.getRole() == Role.LANDLORD) ||
                (sender.getRole() == Role.LANDLORD && receiver.getRole() == Role.TENANT);
        return isTenantLandlord ? ConversationType.TENANT_LANDLORD : ConversationType.LANDLORD_ADMIN;
    }

    private Map<String, Object> toMessageMap(Message m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("senderId", m.getSender().getId());
        map.put("senderName", (m.getSender().getFirstName() != null ? m.getSender().getFirstName() : "") + " " +
                (m.getSender().getLastName() != null ? m.getSender().getLastName() : ""));
        map.put("senderRole", m.getSender().getRole().name());
        map.put("receiverId", m.getReceiver().getId());
        map.put("receiverName", (m.getReceiver().getFirstName() != null ? m.getReceiver().getFirstName() : "") + " " +
                (m.getReceiver().getLastName() != null ? m.getReceiver().getLastName() : ""));
        map.put("content", m.getContent());
        map.put("sentAt", m.getSentAt().toString());
        map.put("readAt", m.getReadAt() != null ? m.getReadAt().toString() : null);
        map.put("conversationType", m.getConversationType().name());
        return map;
    }
}
