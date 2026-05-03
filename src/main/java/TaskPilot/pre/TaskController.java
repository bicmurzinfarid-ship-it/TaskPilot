package TaskPilot.pre;

import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class TaskController {
    private final TaskService taskService;
    public TaskController(TaskService taskService){this.taskService = taskService;}
    @GetMapping("/task")
    public List<Task> findAllTasks(){
        return taskService.findAllTasks();
    }
    @GetMapping("/task/{id}")
    public Task findTaskById(@PathVariable Long id){
        return taskService.findTaskById(id);
    }
    @PostMapping("/task")
    public Task createTask(@RequestBody Task task){
        return taskService.createTask(task);
    }
    @DeleteMapping("/task/{id}")
    public void deleteTask(@PathVariable Long id){
        taskService.deleteTask(id);
    }
    @PatchMapping("/task/{id}/status")
    public Task updateStatus(@PathVariable Long id, @RequestParam TaskStatus status){
        return taskService.updateStatus(id, status);
    }
}
