package TaskPilot.pre;

import lombok.Builder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;

import java.util.HashSet;
import java.util.Set;

@Document
public class ChatRoom {
    @Id
    private String id;
    private String nameChat;
    private Long creatorId;
    private ChatRoomType type;
    private Set<Long> memberIds = new HashSet<>();

    public ChatRoom(){}
    public ChatRoom(String nameChat, Long creatorId, ChatRoomType type, Set<Long> memberIds){
        this.nameChat = nameChat;
        this.creatorId = creatorId;
        this.type = type;
        this.memberIds = memberIds;
    }
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNameChat() {
        return nameChat;
    }

    public void setNameChat(String nameChat){
        this.nameChat = nameChat;
    }

    public Long getCreatorId() {
        return creatorId;
    }

    public void setCreatorId(Long creatorId){
        this.creatorId = creatorId;
    }

    public ChatRoomType getType() {
        return type;
    }

    public void setType(ChatRoomType type) {
        this.type = type;
    }

    public Set<Long> getMemberIds() {
        return memberIds;
    }

    public void setMemberIds(Set<Long> memberIds) {
        this.memberIds = memberIds;
    }

    public boolean isMember(Long userId){
        return memberIds.contains(userId);
    }

    public boolean canManageRoom(Long userId){
        return creatorId.equals(userId);
    }
}
