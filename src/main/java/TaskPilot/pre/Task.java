package TaskPilot.pre;

public class Task {
    private Long id;

    private Long creatorId;
    private Long assigneeId;

    private String title;
    private String description;
    private TaskStatus status;

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
}
