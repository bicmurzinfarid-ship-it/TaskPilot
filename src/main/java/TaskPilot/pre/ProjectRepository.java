package TaskPilot.pre;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Найти все проекты, в которых пользователь является участником.
     * Spring Data JPA автоматически строит JOIN по таблице project_members:
     * SELECT p.* FROM projects p
     * JOIN project_members pm ON pm.project_id = p.id
     * WHERE pm.user_id = :userId
     */
    List<Project> findByMembersId(Long userId);
}
