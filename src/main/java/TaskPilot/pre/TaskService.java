package TaskPilot.pre;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TaskService {
    private final List<Task> tasks = new ArrayList<>();
    private Long nextId = 1L;

    public String getTasksInfo() {
        return "Tasks service is working!";
    }

    public List<Task> findAllTasks() {
        return tasks;
    }
    public Task findTaskById(Long id){
        for(Task task: tasks){
            if(task.getId().equals(id)){return task;}
        }
        throw new RuntimeException("Not found");
    }
    public Task createTask(Task task){
        if(task.getId() != null){
            throw new IllegalArgumentException("Id should be empty");
        }
        if(task.getStatus() != TaskStatus.WAITING){
            throw new IllegalArgumentException("Status should be empty");
        }
        task.setId(nextId++);
        tasks.add(task);
        return task;
    }
}
