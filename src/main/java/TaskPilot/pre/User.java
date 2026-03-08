package TaskPilot.pre;

import java.util.*;

public class User {
    private Long id;

    private String username;
    private String password;
    private String email;

    List<Task> tasks;

    public User(Long id, String username, String password, String email){
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
        this.tasks = new ArrayList<>();
    }

    public void createTask(Long taskID, Long assigneeID, String title, String description){
        tasks.add(new Task(taskID, id, assigneeID, title, description));
    }

    public void viewTasks(){
        for(Task task: tasks){
            System.out.println("Id task: " + task.getId() +
                               ". Id creator task: " + task.getCreatorID() +
                               ". Id assignee task: " + task.getAssigneeId() +
                               ". Title task: " + task.getTitle() +
                               ". Description task: " + task.getDescription() +
                               ". Status task: " + task.getStatus());
        }
    }

    public void changeTaskAssigneeById(Long taskId, Long assigneeId){
        for(Task task: tasks) {
            if (task.getId().equals(taskId)) {
                task.setAssigneeId(assigneeId);
                break;
            }
        }
    }

    public void changeTaskDescriptionById(Long taskId, String description){
        for(Task task: tasks) {
            if (task.getId().equals(taskId)) {
                task.setDescription(description);
                break;
            }
        }
    }

    public void changeTaskStatusById(Long taskId, TaskStatus status){
        for(Task task: tasks){
            if (task.getId().equals(taskId)){
                task.setStatus(status);
                break;
            }
        }
    }

    public void deleteTaskById(Long taskId){
        for(Task task: tasks){
            if (task.getId().equals(taskId)) {
                tasks.remove(task);
                break;
            }
        }
    }
}
