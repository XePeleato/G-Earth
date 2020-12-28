package gmessenger;

import java.time.LocalDateTime;

public class ChatMessage {
    private final String content;
    private final long senderId;

    public boolean isIncoming() {
        return incoming;
    }

    private final boolean incoming;

    public String getContent() {
        return content;
    }

    public long getSenderId() {
        return senderId;
    }

    public LocalDateTime getDate() {
        return date;
    }

    private final LocalDateTime date;

    public ChatMessage(long sender, String text, boolean incoming) {
        senderId = sender;
        content = text;
        date = LocalDateTime.now();
        this.incoming = incoming;
    }
}
