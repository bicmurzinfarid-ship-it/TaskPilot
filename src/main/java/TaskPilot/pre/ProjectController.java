package TaskPilot.pre;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST-контроллер для проектов.
 *
 * Маршруты:
 *   POST   /project                          — создать проект
 *   GET    /project                          — мои проекты
 *   GET    /project/{id}                     — детали проекта
 *   POST   /project/{id}/member/{userId}     — добавить участника
 *   DELETE /project/{id}/member/{userId}     — удалить участника
 *   PUT    /project/{id}/teamlead/{userId}   — назначить тимлида
 *   DELETE /project/{id}/teamlead            — снять тимлида
 *   POST   /project/{id}/task                — создать задачу в проекте
 *   GET    /project/{id}/task                — задачи проекта
 *
 * Все маршруты защищены JWT (настроено в SecurityConfig).
 * Проверка прав происходит в ProjectService.
 */
@RestController
@RequestMapping("/project")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    // ─── Проект ───────────────────────────────────────────────────────────────

    /**
     * Создать проект.
     * Тело: { "name": "Название проекта" }
     * Текущий пользователь автоматически становится создателем и участником.
     */
    @PostMapping
    public ResponseEntity<Project> createProject(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        return ResponseEntity.ok(projectService.createProject(name));
    }

    /**
     * Получить все проекты текущего пользователя.
     */
    @GetMapping
    public ResponseEntity<List<Project>> getMyProjects() {
        return ResponseEntity.ok(projectService.getMyProjects());
    }

    /**
     * Получить детали конкретного проекта (включая список участников).
     * Доступно только участникам проекта.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Project> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProject(id));
    }

    // ─── Удаление проекта ────────────────────────────────────────────────────

    /**
     * Удалить проект (только создатель). Все задачи проекта удаляются тоже.
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    // ─── Участники ────────────────────────────────────────────────────────────

    /**
     * Добавить участника в проект.
     * Права: создатель или тимлид.
     */
    @PostMapping("/{id}/member/{userId}")
    public ResponseEntity<Project> addMember(@PathVariable Long id,
                                             @PathVariable Long userId) {
        return ResponseEntity.ok(projectService.addMember(id, userId));
    }

    /**
     * Удалить участника из проекта.
     * Права: создатель или тимлид. Создателя удалить нельзя.
     */
    @DeleteMapping("/{id}/member/{userId}")
    public ResponseEntity<Project> removeMember(@PathVariable Long id,
                                                @PathVariable Long userId) {
        return ResponseEntity.ok(projectService.removeMember(id, userId));
    }

    // ─── Тимлид ───────────────────────────────────────────────────────────────

    /**
     * Назначить тимлида.
     * Права: только создатель. Пользователь должен уже быть участником.
     */
    @PutMapping("/{id}/teamlead/{userId}")
    public ResponseEntity<Project> setTeamLead(@PathVariable Long id,
                                               @PathVariable Long userId) {
        return ResponseEntity.ok(projectService.setTeamLead(id, userId));
    }

    /**
     * Снять тимлида (сбросить роль).
     * Права: только создатель.
     */
    @DeleteMapping("/{id}/teamlead")
    public ResponseEntity<Project> removeTeamLead(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.removeTeamLead(id));
    }

    // ─── Задачи проекта ───────────────────────────────────────────────────────

    /**
     * Создать задачу в проекте.
     * Права: создатель или тимлид.
     * Тело: { "title": "...", "description": "...", "assigneeId": 2, "deadline": "2026-04-15T12:00:00", "importance": 4 }
     */
    @PostMapping("/{id}/task")
    public ResponseEntity<Task> createTask(@PathVariable Long id,
                                           @RequestBody Task task) {
        return ResponseEntity.ok(projectService.createTask(id, task));
    }

    /**
     * Получить все задачи проекта.
     * Права: любой участник проекта.
     */
    @GetMapping("/{id}/task")
    public ResponseEntity<List<Task>> getProjectTasks(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectTasks(id));
    }
}
