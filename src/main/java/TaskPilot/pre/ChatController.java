package TaskPilot.pre;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@RestController
public class ChatController {

    @Autowired private SimpMessagingTemplate messagingTemplate;
    @Autowired private ChatMessageService chatMessageService;
    @Autowired private ChatRoomService chatRoomService;
    @Autowired private UserRepository userRepository;

    public ChatController(SimpMessagingTemplate messagingTemplate,
                          ChatMessageService chatMessageService,
                          ChatRoomService chatRoomService,
                          UserRepository userRepository){
        this.messagingTemplate = messagingTemplate;
        this.chatMessageService = chatMessageService;
        this.chatRoomService = chatRoomService;
        this.userRepository = userRepository;
    }

    @MessageMapping("/chat")
    public void processMessage(@Payload ChatMessage chatMessage, Principal principal) {
        String username = principal.getName();
        Long senderId = userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
        chatMessage.setSenderId(senderId);
        chatMessage.setSenderName(username);
        ChatMessage saved = chatMessageService.save(chatMessage);

        ChatRoom room = chatRoomService.getRoom(chatMessage.getRoomId());

        for(Long member: room.getMemberIds()){
            if(!member.equals(senderId)){
                messagingTemplate.convertAndSendToUser(
                        String.valueOf(member),
                        "/queue/messages",
                        saved
                );
            }
        }
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
        List<Long> members = (List<Long>) body.get("members");
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

    public Long getCurrentUserId(){
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .map(User::getId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }
}
