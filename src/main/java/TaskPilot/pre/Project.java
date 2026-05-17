package TaskPilot.pre;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

/**
 * Проект — основная единица организации работы.
 *
 * Роли внутри проекта:
 * - Создатель (creator_id): единственный, кто может назначить тимлида
 *   и удалить проект. Автоматически становится участником при создании.
 * - Тимлид (team_lead_id): назначается создателем. Может добавлять
 *   участников и создавать/назначать задачи. Поле nullable — проект
 *   может существовать без тимлида.
 * - Участник (project_members): может видеть задачи проекта и менять
 *   статус своих задач.
 *
 * Важно: создатель и тимлид тоже являются участниками (есть в members).
 * Это упрощает проверку доступа — достаточно проверить членство.
 */
@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    // Кто создал проект. Не меняется никогда.
    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    // Кто является тимлидом. Null = тимлид ещё не назначен.
    // Назначить может только создатель.
    @Column(name = "team_lead_id")
    private Long teamLeadId;

    @Column(name = "chat_room_id")
    private String chatRoomId;

    /**
     * Список участников проекта.
     *
     * @ManyToMany — один проект имеет много участников,
     *               один пользователь может быть в многих проектах.
     *
     * @JoinTable создаёт таблицу project_members:
     *   project_id FK → projects.id
     *   user_id    FK → users.id
     *
     * FetchType.LAZY — участники НЕ загружаются автоматически при каждом
     * запросе проекта. Загружаются только когда явно обращаемся к members.
     * Это важно для производительности.
     */
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "project_members",
            joinColumns = @JoinColumn(name = "project_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private Set<User> members = new HashSet<>();

    public Project() {}

    public Project(String name, Long creatorId) {
        this.name = name;
        this.creatorId = creatorId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public Long getTeamLeadId() { return teamLeadId; }
    public void setTeamLeadId(Long teamLeadId) { this.teamLeadId = teamLeadId; }

    public Set<User> getMembers() { return members; }
    public void setMembers(Set<User> members) { this.members = members; }

    // Вспомогательные методы для проверки ролей

    public boolean isCreator(Long userId) {
        return creatorId.equals(userId);
    }

    public boolean isTeamLead(Long userId) {
        return teamLeadId != null && teamLeadId.equals(userId);
    }

    /** Создатель или тимлид — могут управлять задачами и участниками */
    public boolean isManager(Long userId) {
        return isCreator(userId) || isTeamLead(userId);
    }

    public String getChatRoomId() { return chatRoomId; }
    public void setChatRoomId(String chatRoomId) { this.chatRoomId = chatRoomId; }

    public boolean isMember(Long userId) {
        return members.stream().anyMatch(u -> u.getId().equals(userId));
    }
}
