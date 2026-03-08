package TaskPilot.pre;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class TaskPilotApplication {

	public static void main(String[] args) {
        User user1 = new User(1L, "hh", "jg", "jgf");
        user1.createTask(1L, 2L, "kjhgf", "lkjhgf");
        SpringApplication.run(TaskPilotApplication.class, args);
	}

}
