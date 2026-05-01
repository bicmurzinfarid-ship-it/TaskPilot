package TaskPilot.pre;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;


@SpringBootApplication()
@EnableJpaRepositories(basePackages = "TaskPilot.pre")
@EnableMongoRepositories(basePackages = "TaskPilot.pre")
public class TaskPilotApplication {

	public static void main(String[] args) {
        SpringApplication.run(TaskPilotApplication.class, args);
	}

}
