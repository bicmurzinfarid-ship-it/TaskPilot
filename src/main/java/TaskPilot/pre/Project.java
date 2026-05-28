package TaskPilot.pre;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "creator_id", nullable = false)
    private Long creatorId;

    // null = тимлид не назначен; назначает только создатель
    @Column(name = "team_lead_id")
    private Long teamLeadId;

    @Column(name = "chat_room_id")
    private String chatRoomId;

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

    public boolean isCreator(Long userId) {
        return creatorId.equals(userId);
    }

    public boolean isTeamLead(Long userId) {
        return teamLeadId != null && teamLeadId.equals(userId);
    }

    // создатель или тимлид — могут управлять задачами и участниками
    public boolean isManager(Long userId) {
        return isCreator(userId) || isTeamLead(userId);
    }

    public String getChatRoomId() { return chatRoomId; }
    public void setChatRoomId(String chatRoomId) { this.chatRoomId = chatRoomId; }

    public boolean isMember(Long userId) {
        return members.stream().anyMatch(u -> u.getId().equals(userId));
    }
}
