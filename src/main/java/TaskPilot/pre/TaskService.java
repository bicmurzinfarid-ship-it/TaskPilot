package TaskPilot.pre;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TaskService {
    private final TaskRepository taskRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;

    public TaskService(TaskRepository taskRepository,
                       ProjectRepository projectRepository,
                       UserRepository userRepository) {
        this.taskRepository = taskRepository;
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
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

        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User currentUser = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));

        Project project = projectRepository.findById(task.getProjectId())
                .orElseThrow(() -> new RuntimeException("Проект не найден"));

        boolean isAssignee = currentUser.getId().equals(task.getAssigneeId());
        boolean isManager  = project.isManager(currentUser.getId());

        if (!isAssignee && !isManager) {
            throw new SecurityException("Нет прав для изменения статуса этой задачи");
        }
        // Исполнитель не может сам выставить READY — только тимлид/создатель подтверждают
        if (isAssignee && !isManager && status == TaskStatus.READY) {
            throw new SecurityException("Только тимлид или создатель могут подтвердить выполнение задачи");
        }

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
