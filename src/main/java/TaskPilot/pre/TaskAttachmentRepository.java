package TaskPilot.pre;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskAttachmentRepository extends JpaRepository<TaskAttachment, Long> {
    List<TaskAttachment> findByTaskId(Long taskId);
    long countByTaskId(Long taskId);
    void deleteByTaskId(Long taskId);
}
