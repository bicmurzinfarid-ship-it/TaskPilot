package TaskPilot.pre;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {
    private final TaskRepository taskRepository;

    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public String getTasksInfo() {
        return "Tasks service is working!";
    }

    public List<Task> findAllTasks() {
        return taskRepository.findAll();
    }

    public Task findTaskById(Long id) {
        return taskRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Not found"));
    }

    public void deleteTask(Long id) {
        if (!taskRepository.existsById(id)) {
            throw new RuntimeException("Not found");
        }
        taskRepository.deleteById(id);
    }

    public Task updateStatus(Long id, TaskStatus status) {
        Task task = findTaskById(id);
        task.setStatus(status);
        return taskRepository.save(task);
    }

    public Task createTask(Task task) {
        if (task.getId() != null) {
            throw new IllegalArgumentException("Id should be empty");
        }
        if (task.getStatus() != null && task.getStatus() != TaskStatus.WAITING) {
            throw new IllegalArgumentException("Status should be empty or WAITING");
        }
        if (task.getImportance() != null && (task.getImportance() < 1 || task.getImportance() > 5)) {
            throw new IllegalArgumentException("Importance must be between 1 and 5");
        }
        if (task.getStatus() == null) {
            task.setStatus(TaskStatus.WAITING);
        }
        return taskRepository.save(task);
    }
}
