package TaskPilot.pre;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.user.SimpUserRegistry;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);


    private SimpMessagingTemplate messagingTemplate;
    private ChatMessageService chatMessageService;
    private ChatRoomService chatRoomService;
    private UserRepository userRepository;


    public ChatController(SimpMessagingTemplate messagingTemplate,
                          ChatMessageService chatMessageService,
                          ChatRoomService chatRoomService,
                          UserRepository userRepository){
        this.messagingTemplate = messagingTemplate;
        this.chatMessageService = chatMessageService;
        this.chatRoomService = chatRoomService;
        this.userRepository = userRepository;
    }

    @MessageExceptionHandler
    public void handleException(Exception e, Principal principal) {
        log.error("WebSocket error for {}: {}", principal != null ? principal.getName() : "unknown", e.getMessage(), e);
    }

    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessage chatMessage, Principal principal) {
        if (principal == null) {
            throw new SecurityException("Не аутентифицирован");
        }
        String username = principal.getName();
        Long senderId = userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        chatMessage.setSenderId(senderId);
        chatMessage.setSenderName(username);
        ChatMessage saved = chatMessageService.save(chatMessage);

        messagingTemplate.convertAndSend("/topic/chat/" + chatMessage.getRoomId(), saved);
    }

    @PostMapping("/chat/private")
    public ResponseEntity<ChatRoom> getOrCreatePrivateChat(@RequestParam String name,
                                                           @RequestParam Long userId){
        Long currentUserId = getCurrentUserId();
        ChatRoom room = chatRoomService.getOrCreatePrivateChat(currentUserId, userId, name);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/chat/group")
    public ResponseEntity<ChatRoom> getOrCreateGroupChat(@RequestBody Map<String, Object> body){
        Long currentUserId = getCurrentUserId();
        String name = (String) body.get("name");
        @SuppressWarnings("unchecked")
        List<Object> rawMembers = (List<Object>) body.get("members");
        List<Long> members = rawMembers.stream()
                .map(obj -> ((Number) obj).longValue())
                .toList();
        ChatRoom room = chatRoomService.createGroupChat(currentUserId, name, members);
        return ResponseEntity.ok(room);
    }

    @PostMapping("/chat/group/{roomId}/member")
    public ResponseEntity<ChatRoom> addMember(@PathVariable String roomId, @RequestParam Long userId){
        Long currentUserId = getCurrentUserId();
        ChatRoom room = chatRoomService.addMemberToGroup(currentUserId, roomId, userId);
        return ResponseEntity.ok(room);
    }

    @DeleteMapping("/chat/group/{roomId}/member/{userId}")
    public ResponseEntity<ChatRoom> removeMember(@PathVariable String roomId, @PathVariable Long userId){
        Long currentUserId = getCurrentUserId();
        ChatRoom room = chatRoomService.removeMemberToGroup(currentUserId, roomId, userId);
        return ResponseEntity.ok(room);
    }

    @GetMapping("/chat/rooms")
    public ResponseEntity<List<ChatRoom>> getMyRooms(){
        Long currentUserId = getCurrentUserId();
        List<ChatRoom> rooms = chatRoomService.getUserRooms(currentUserId);
        return ResponseEntity.ok(rooms);
    }

    @GetMapping("/chat/rooms/{roomId}/messages")
    public ResponseEntity<List<ChatMessage>> getRoomMessages(@PathVariable String roomId){
        Long currentUserId = getCurrentUserId();
        List<ChatMessage> messages = chatMessageService.findByRoomId(roomId, currentUserId);
        return ResponseEntity.ok(messages);
    }

    @DeleteMapping("/chat/rooms/{roomId}")
    public ResponseEntity<Void> deleteChat(@PathVariable String roomId) {
        Long currentUserId = getCurrentUserId();
        ChatRoom room = chatRoomService.getRoom(roomId);

        if (!room.getCreatorId().equals(currentUserId)) {
            throw new SecurityException("Только создатель может удалить чат");
        }

        chatRoomService.deleteChat(roomId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/chat/rooms/{roomId}")
    public ResponseEntity<ChatRoom> getRoom(@PathVariable String roomId) {
        Long currentUserId = getCurrentUserId();
        ChatRoom room = chatRoomService.getRoom(roomId);
        if (!room.isMember(currentUserId)) {
            throw new SecurityException("Нет доступа");
        }
        return ResponseEntity.ok(room);
    }

    public Long getCurrentUserId(){
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }
}
