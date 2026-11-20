package no.trafonsjakk.admin;

import java.util.Map;
import no.trafonsjakk.common.ApiException;
import no.trafonsjakk.notify.ChatNotifications;
import no.trafonsjakk.notify.GoogleChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Admin: is Google Chat connected, and does a message get through. The webhook link itself is never shown. */
@RestController
@RequestMapping("/api/admin/chat")
public class ChatAdminController {

    private final GoogleChatClient chat;
    private final ChatNotifications notifications;

    public ChatAdminController(GoogleChatClient chat, ChatNotifications notifications) {
        this.chat = chat;
        this.notifications = notifications;
    }

    public record ChatStatus(boolean configured) {
    }

    @GetMapping
    ChatStatus status() {
        return new ChatStatus(chat.configured());
    }

    @PostMapping("/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void test() {
        if (!chat.configured()) {
            throw ApiException.conflict("chat.not_configured", "Google Chat isn't set up on the server");
        }
        String failure = notifications.sendTest();
        if (failure != null) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "chat.failed", "Google Chat didn't accept the message: " + failure,
                    Map.of("reason", failure), null);
        }
    }
}
