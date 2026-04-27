package TaskPilot.pre;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ChatMessageService {
    @Autowired private ChatMessageRepository repository;
    @Autowired private ChatRoomService chatRoomService;
    public ChatMessageService(ChatMessageRepository repository, ChatRoomService chatRoomService){
        this.repository = repository;
        this.chatRoomService = chatRoomService;
    }

    public ChatMessage save(ChatMessage message) {
        if (!chatRoomService.isRoomMember(message.getRoomId(), message.getSenderId())) {
            throw new SecurityException("Нет доступа к этой комнате");
        }
        return repository.save(message);
    }

    public List<ChatMessage> findByRoomId(String roomId, Long userId){
        if(!chatRoomService.isRoomMember(roomId, userId)){
            throw new SecurityException("Нет доступа к этой комнате");
        }
        return repository.findByRoomId(roomId);
    }

    public ChatMessage findById(String messageId){
        Optional<ChatMessage> message = repository.findById(messageId);
        if(!message.isPresent()){
            throw new IllegalArgumentException("Нет сообщения с идентификатором " + messageId);
        }
        return message.get();
    }
}
