package TaskPilot.pre;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChatRoomRepository extends MongoRepository<ChatRoom, String> {
    @Query("{ 'type': ?0, 'memberIds': { '$all': [?1, ?2] } }")
    Optional<ChatRoom> findPrivateChatByMembers(ChatRoomType type, Long userId1, Long userId2);

    @Query(value = "{ 'type': ?0, 'memberIds': { '$all': [?1, ?2] } }", exists = true)
    boolean existsPrivateChatByMembers(ChatRoomType type, Long userId1, Long userId2);

    List<ChatRoom> findByMemberIdsContaining(Long user);
}
