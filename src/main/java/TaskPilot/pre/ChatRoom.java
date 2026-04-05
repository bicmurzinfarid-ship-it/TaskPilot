package TaskPilot.pre;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;


@Builder
@Document
public class ChatRoom {
    @Id
    private String id;
    private String chatId;
    private String senderId;
    private String recipientId;

    public ChatRoom(){}
    public ChatRoom(String id, String chatId, String senderId, String recipientId){
        this.id = id;
        this.chatId = chatId;
        this.recipientId = recipientId;
        this.senderId = senderId;
    }
    public String getId() {
        return id;
    }

    public String getChatId() {
        return chatId;
    }

    public String getSenderId() {
        return senderId;
    }

    public String getRecipientId() {
        return recipientId;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setChatId(String chatId) {
        this.chatId = chatId;
    }

    public void setSenderId(String senderId) {
        this.senderId = senderId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }
}
