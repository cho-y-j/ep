package com.skep.notification;

import com.skep.common.PageResponse;
import com.skep.notification.dto.NotificationResponse;
import com.skep.security.AuthenticatedUser;
import com.skep.security.CurrentUser;
import org.springframework.web.bind.annotation.*;

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
            @RequestParam(defaultValue = "20") int size
    ) {
        var pg = service.list(actor, page, size);
        return PageResponse.of(pg, NotificationResponse::from);
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
