package TaskPilot.pre;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Матрица Эйзенхауэра: срочность вычисляется по дедлайну, важность — по шкале 1-5.
 * Расчёт квадранта: getEisenhowerQuadrant().
 */
@Entity
@Table(name = "tasks")
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "creator_id")
    private Long creatorId;

    @Column(name = "assignee_id")
    private Long assigneeId;

    @Column(nullable = false)
    private String title;

    @Column(length = 2000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255)")
    private TaskStatus status = TaskStatus.WAITING;

    /** Дедлайн задачи. Срочность вычисляется по времени до дедлайна. */
    private LocalDateTime deadline;

    /** Важность 1-5 (5 = максимально важно) */
    private Integer importance;

    private static final int HIGH_THRESHOLD = 4;

    public Task() {}
    public Task(Long id, Long creatorID, Long assigneeId, String title, String description){
        this.id = id;
        this.creatorId = creatorID;
        this.assigneeId = assigneeId;
        this.title = title;
        this.description = description;
        status = TaskStatus.WAITING;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getProjectId() { return projectId; }
    public void setProjectId(Long projectId) { this.projectId = projectId; }

    public Long getCreatorId() { return creatorId; }
    public void setCreatorId(Long creatorId) { this.creatorId = creatorId; }

    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }

    public LocalDateTime getDeadline() { return deadline; }
    public void setDeadline(LocalDateTime deadline) { this.deadline = deadline; }

    public Integer getImportance() { return importance; }
    public void setImportance(Integer importance) { this.importance = importance; }

    /**
     * Вычисляет срочность 1-5 по дедлайну относительно текущего времени.
     * Просрочено / < 24ч → 5; 1-3 дня → 4; 3-7 дней → 3; 1-4 нед → 2; > 4 нед → 1.
     */
    @Transient
    public Integer getUrgency() {
        if (deadline == null) return null;
        long hours = ChronoUnit.HOURS.between(LocalDateTime.now(), deadline);
        if (hours < 0) return 5;           // просрочено
        if (hours < 24) return 5;          // < 24 ч
        if (hours < 48) return 4;          // 1-2 дня
        if (hours < 168) return 3;         // 2-7 дней
        if (hours < 672) return 2;         // 1-4 недели
        return 1;
    }

    /**
     * Вычисляет квадрант матрицы Эйзенхауэра.
     * Важность: 3 = важно, 2 = не очень важно, 1 = не важно.
     * Срочность: urgency >= 4 (≤3 дней до дедлайна).
     */
    @Transient
    public EisenhowerQuadrant getEisenhowerQuadrant() {
        Integer urgency = getUrgency();
        if (urgency == null || importance == null) return null;
        boolean isUrgent = urgency >= HIGH_THRESHOLD;
        if (isUrgent) {
            if (importance >= 3) return EisenhowerQuadrant.URGENT_IMPORTANT;
            if (importance == 2) return EisenhowerQuadrant.URGENT_SOMEWHAT_IMPORTANT;
            return EisenhowerQuadrant.URGENT_NOT_IMPORTANT;
        } else {
            if (importance >= 3) return EisenhowerQuadrant.NOT_URGENT_IMPORTANT;
            if (importance == 2) return EisenhowerQuadrant.NOT_URGENT_SOMEWHAT_IMPORTANT;
            return EisenhowerQuadrant.NOT_URGENT_NOT_IMPORTANT;
        }
    }
}
