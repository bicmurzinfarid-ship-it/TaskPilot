package TaskPilot.pre;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Найти все задачи проекта.
     * Spring Data JPA генерирует: SELECT * FROM tasks WHERE project_id = ?
     */
    List<Task> findByProjectId(Long projectId);

    void deleteByProjectId(Long projectId);
}
