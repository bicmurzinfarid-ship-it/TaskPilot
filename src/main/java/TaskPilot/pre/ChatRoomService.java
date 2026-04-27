package TaskPilot.pre;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ChatRoomService {

    @Autowired
    private ChatRoomRepository chatRoomRepository;
    private UserRepository userRepository;

    public ChatRoomService(ChatRoomRepository chatRoomRepository, UserRepository userRepository){
        this.chatRoomRepository = chatRoomRepository;
        this.userRepository = userRepository;
    }

    public ChatRoom getOrCreatePrivateChat(Long userId1, Long userId2){
        Optional<ChatRoom> room = chatRoomRepository.findByTypeAndMemberIdsContainingAndMemberIdsContaining(
                ChatRoomType.PRIVATE, userId1, userId2);
        if(room.isPresent()){
            return room.get();
        }

        Optional<User> user1 = userRepository.findById(userId1);
        if (!user1.isPresent()){
            throw new IllegalArgumentException("Пользователь не найден: " + userId1);
        }
        Optional<User> user2 = userRepository.findById(userId2);
        if (!user2.isPresent()){
            throw new IllegalArgumentException("Пользователь не найден: " + userId2);
        }
        Set<Long> memberIds = new HashSet<>();
        memberIds.add(userId1);
        memberIds.add(userId2);
        ChatRoom newRoom = new ChatRoom(
                user1.get().getUsername() + " - " + userRepository.findById(userId2).get().getUsername(),
                userId1, ChatRoomType.PRIVATE, memberIds);
        return chatRoomRepository.save(newRoom);
    }

    public ChatRoom createGroupChat(Long creatorId, String name, List<Long> members){
        if (name == null || name.isBlank()){
            throw new IllegalArgumentException("Название комнаты не может быть пустым");
        }
        Set<Long> memberIds = new HashSet<>(members);
        memberIds.add(creatorId);
        ChatRoom room = new ChatRoom(name, creatorId, ChatRoomType.GROUP, memberIds);
        return chatRoomRepository.save(room);
    }

    public ChatRoom addMemberToGroup(Long creatorId, String roomId, Long newMemberId){
        ChatRoom room = getRoom(roomId);

        if(!room.gettypeRoom().equals(ChatRoomType.GROUP)){
            throw new IllegalArgumentException("Нельзя добавлять участников в приватный чат");
        }
        if(!room.canManageRoom(creatorId)){
            throw new IllegalArgumentException("Только создатель может добавлять участников");
        }
        if(isRoomMember(roomId, newMemberId)){
            throw new IllegalArgumentException("Пользователь уже в комнате");
        }
        room.getMemberIds().add(newMemberId);
        return chatRoomRepository.save(room);
    }

    public ChatRoom removeMemberToGroup(Long creatorId, String roomId, Long MemberId){
        ChatRoom room = getRoom(roomId);

        if(!room.gettypeRoom().equals(ChatRoomType.GROUP)){
            throw new IllegalArgumentException("Нельзя удалять участников из приватного чата");
        }
        if(!room.canManageRoom(creatorId)){
            throw new IllegalArgumentException("Только создатель может удалять участников");
        }
        if(room.getCreatorId().equals(MemberId)){
            throw new IllegalArgumentException("Нельзя удалить создателя");
        }
        room.getMemberIds().remove(MemberId);
        return chatRoomRepository.save(room);
    }

    public List<ChatRoom> getUserRooms(Long userId){
        return chatRoomRepository.findByMemberIdsContaining(userId);
    }

    public ChatRoom getRoom(String roomId){
        Optional<ChatRoom> existing = chatRoomRepository.findById(roomId);
        if(!existing.isPresent()){
            throw new IllegalArgumentException("Комната не найдена");
        }
        return existing.get();
    }

    public boolean isRoomMember(String roomId, Long userId) {
        ChatRoom room = getRoom(roomId);
        return room.isMember(userId);
    }
}