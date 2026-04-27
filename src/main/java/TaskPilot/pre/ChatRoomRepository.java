package TaskPilot.pre;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {
    Optional<ChatRoom> findByTypeAndMemberIdsContainingAndMemberIdsContaining(ChatRoomType roomType, Long userId1, Long userId2);
    List<ChatRoom> findByMemberIdsContaining(Long user);
    boolean existsByTypeAndMemberIdsContainingAndMemberIdsContaining(ChatRoomType type, Long user1, Long user2);
}
