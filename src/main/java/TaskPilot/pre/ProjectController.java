package TaskPilot.pre;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/project")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @PostMapping
    public ResponseEntity<Project> createProject(@RequestBody Map<String, String> body) {
        String name = body.get("name");
        return ResponseEntity.ok(projectService.createProject(name));
    }

    @GetMapping
    public ResponseEntity<List<Project>> getMyProjects() {
        return ResponseEntity.ok(projectService.getMyProjects());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Project> getProject(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProject(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProject(@PathVariable Long id) {
        projectService.deleteProject(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/member/{userId}")
    public ResponseEntity<Project> addMember(@PathVariable Long id,
                                             @PathVariable Long userId) {
        return ResponseEntity.ok(projectService.addMember(id, userId));
    }

    @DeleteMapping("/{id}/member/{userId}")
    public ResponseEntity<Project> removeMember(@PathVariable Long id,
                                                @PathVariable Long userId) {
        return ResponseEntity.ok(projectService.removeMember(id, userId));
    }

    @PutMapping("/{id}/teamlead/{userId}")
    public ResponseEntity<Project> setTeamLead(@PathVariable Long id,
                                               @PathVariable Long userId) {
        return ResponseEntity.ok(projectService.setTeamLead(id, userId));
    }

    @DeleteMapping("/{id}/teamlead")
    public ResponseEntity<Project> removeTeamLead(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.removeTeamLead(id));
    }

    @PostMapping("/{id}/chat")
    public ResponseEntity<Project> createProjectChat(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.createProjectChat(id));
    }

    @PostMapping("/{id}/task")
    public ResponseEntity<Task> createTask(@PathVariable Long id,
                                           @RequestBody Task task) {
        return ResponseEntity.ok(projectService.createTask(id, task));
    }

    @GetMapping("/{id}/task")
    public ResponseEntity<List<Task>> getProjectTasks(@PathVariable Long id) {
        return ResponseEntity.ok(projectService.getProjectTasks(id));
    }
}
