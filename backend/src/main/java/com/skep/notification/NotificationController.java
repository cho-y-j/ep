package com.skep.notification;

import com.skep.common.PageResponse;
import com.skep.notification.dto.NotificationResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @GetMapping
    public PageResponse<NotificationResponse> list(
            @CurrentUser AuthenticatedUser actor,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) Boolean unread,
            @RequestParam(required = false) String types,
            @RequestParam(required = false) String fromDate,
            @RequestParam(required = false) String q
    ) {
        List<String> typeList = (types == null || types.isBlank()) ? null
                : Arrays.stream(types.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
        LocalDate from = parseDate(fromDate);
        var pg = service.list(actor, page, size, unread, typeList, from, q);
        return PageResponse.of(pg, NotificationResponse::from);
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }

    @GetMapping("/unread-count")
    public UnreadResponse unreadCount(@CurrentUser AuthenticatedUser actor) {
        return new UnreadResponse(service.unreadCount(actor));
    }

    @PostMapping("/{id}/read")
    public NotificationResponse markRead(@PathVariable Long id, @CurrentUser AuthenticatedUser actor) {
        return NotificationResponse.from(service.markRead(id, actor));
    }

    @PostMapping("/read-all")
    public ReadAllResponse markAllRead(@CurrentUser AuthenticatedUser actor) {
        return new ReadAllResponse(service.markAllRead(actor));
    }

    public record UnreadResponse(long unread) {}
    public record ReadAllResponse(int count) {}
}
