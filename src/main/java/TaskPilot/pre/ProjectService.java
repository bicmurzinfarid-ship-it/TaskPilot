package TaskPilot.pre;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final TaskRepository taskRepository;

    private final ChatRoomService chatRoomService;

    public ProjectService(ProjectRepository projectRepository,
                          UserRepository userRepository,
                          TaskRepository taskRepository,
                          ChatRoomService chatRoomService) {
        this.projectRepository = projectRepository;
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.chatRoomService = chatRoomService;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Текущий пользователь не найден"));
    }

    private Project getProjectOrThrow(Long projectId) {
        return projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException("Проект не найден: " + projectId));
    }

    private User getUserOrThrow(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден: " + userId));
    }

    @Transactional
    public Project createProject(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Название проекта не может быть пустым");
        }

        User creator = getCurrentUser();
        Project project = new Project(name, creator.getId());

        // Создатель сразу становится участником
        project.getMembers().add(creator);

        return projectRepository.save(project);
    }

    @Transactional(readOnly = true)
    public Project getProject(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        User current = getCurrentUser();

        if (!project.isMember(current.getId())) {
            throw new SecurityException("Нет доступа к этому проекту");
        }
        return project;
    }

    @Transactional(readOnly = true)
    public List<Project> getMyProjects() {
        User current = getCurrentUser();
        return projectRepository.findByMembersId(current.getId());
    }

    @Transactional
    public Project addMember(Long projectId, Long userId) {
        Project project = getProjectOrThrow(projectId);
        User current = getCurrentUser();

        if (!project.isManager(current.getId())) {
            throw new SecurityException("Только создатель или тимлид могут добавлять участников");
        }

        User newMember = getUserOrThrow(userId);

        if (project.isMember(userId)) {
            throw new IllegalArgumentException("Пользователь уже является участником проекта");
        }

        project.getMembers().add(newMember);
        return projectRepository.save(project);
    }

    @Transactional
    public Project removeMember(Long projectId, Long userId) {
        Project project = getProjectOrThrow(projectId);
        User current = getCurrentUser();

        if (!project.isManager(current.getId())) {
            throw new SecurityException("Только создатель или тимлид могут удалять участников");
        }
        if (project.isCreator(userId)) {
            throw new IllegalArgumentException("Нельзя удалить создателя из проекта");
        }

        project.getMembers().removeIf(u -> u.getId().equals(userId));

        // Если удалили тимлида — сбрасываем роль тимлида
        if (project.isTeamLead(userId)) {
            project.setTeamLeadId(null);
        }

        return projectRepository.save(project);
    }

    @Transactional
    public Project setTeamLead(Long projectId, Long userId) {
        Project project = getProjectOrThrow(projectId);
        User current = getCurrentUser();

        // Только создатель может назначать тимлида — это ключевое правило
        if (!project.isCreator(current.getId())) {
            throw new SecurityException("Только создатель может назначить тимлида");
        }
        if (!project.isMember(userId)) {
            throw new IllegalArgumentException("Тимлид должен быть участником проекта. Сначала добавьте его.");
        }

        project.setTeamLeadId(userId);
        return projectRepository.save(project);
    }

    @Transactional
    public Project removeTeamLead(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        User current = getCurrentUser();

        if (!project.isCreator(current.getId())) {
            throw new SecurityException("Только создатель может снять тимлида");
        }

        project.setTeamLeadId(null);
        return projectRepository.save(project);
    }

    @Transactional
    public void deleteProject(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        User current = getCurrentUser();

        if (!project.isCreator(current.getId())) {
            throw new SecurityException("Только создатель может удалить проект");
        }

        taskRepository.deleteByProjectId(projectId);
        projectRepository.delete(project);
    }

    @Transactional
    public Task createTask(Long projectId, Task task) {
        Project project = getProjectOrThrow(projectId);
        User current = getCurrentUser();

        if (!project.isManager(current.getId())) {
            throw new SecurityException("Только создатель или тимлид могут создавать задачи");
        }
        if (task.getAssigneeId() != null && !project.isMember(task.getAssigneeId())) {
            throw new IllegalArgumentException("Исполнитель должен быть участником проекта");
        }
        if (task.getImportance() != null && (task.getImportance() < 1 || task.getImportance() > 5)) {
            throw new IllegalArgumentException("Важность должна быть от 1 до 5");
        }

        task.setId(null);
        task.setProjectId(projectId);
        task.setCreatorId(current.getId());
        task.setStatus(TaskStatus.WAITING);

        return taskRepository.save(task);
    }

    @Transactional(readOnly = true)
    public List<Task> getProjectTasks(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        User current = getCurrentUser();

        if (!project.isMember(current.getId())) {
            throw new SecurityException("Нет доступа к этому проекту");
        }

        return taskRepository.findByProjectId(projectId);
    }

    @Transactional
    public Project createProjectChat(Long projectId) {
        Project project = getProjectOrThrow(projectId);
        User current = getCurrentUser();

        if (!project.isManager(current.getId())) {
            throw new SecurityException("Только создатель или тимлид может создать чат");
        }
        if (project.getChatRoomId() != null) {
            return project;
        }

        List<Long> memberIds = project.getMembers().stream()
                .map(User::getId)
                .filter(id -> !id.equals(current.getId()))
                .toList();

        ChatRoom room = chatRoomService.createGroupChat(current.getId(), project.getName(), memberIds);
        project.setChatRoomId(room.getId());
        return projectRepository.save(project);
    }
}
