package TaskPilot.pre;

public class Task {
    private final Long id;

    private final Long creatorId;
    private Long assigneeId;

    private final String title;
    private String description;
    private TaskStatus status;
    public Task(Long id, Long creatorID, Long assigneeId, String title, String description){
        this.id = id;
        this.creatorId = creatorID;
        this.assigneeId = assigneeId;
        this.title = title;
        this.description = description;
        status = TaskStatus.WAITING;
    }

    public Long getId() {
        return id;
    }

    public Long getCreatorID() {
        return creatorId;
    }

    public Long getAssigneeId() {
        return assigneeId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setAssigneeId(Long assigneeId) {
        this.assigneeId = assigneeId;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }
}
