package TaskPilot.pre;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.UUID;

@Service
public class TaskAttachmentService {

    public static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    public static final int MAX_FILES_PER_TASK = 5;

    @Value("${taskpilot.upload.dir:./uploads}")
    private String uploadDir;

    private final TaskAttachmentRepository attachmentRepo;
    private final TaskRepository taskRepo;
    private final ProjectRepository projectRepo;
    private final UserRepository userRepo;

    public TaskAttachmentService(TaskAttachmentRepository attachmentRepo,
                                 TaskRepository taskRepo,
                                 ProjectRepository projectRepo,
                                 UserRepository userRepo) {
        this.attachmentRepo = attachmentRepo;
        this.taskRepo = taskRepo;
        this.projectRepo = projectRepo;
        this.userRepo = userRepo;
    }

    private User getCurrentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Пользователь не найден"));
    }

    private void checkTaskAccess(Task task) {
        User current = getCurrentUser();
        Project project = projectRepo.findById(task.getProjectId())
                .orElseThrow(() -> new RuntimeException("Проект не найден"));
        if (!project.isMember(current.getId())) {
            throw new SecurityException("Нет доступа к этой задаче");
        }
    }

    private void checkUploadAccess(Task task) {
        User current = getCurrentUser();
        Project project = projectRepo.findById(task.getProjectId())
                .orElseThrow(() -> new RuntimeException("Проект не найден"));
        boolean isAssignee = current.getId().equals(task.getAssigneeId());
        boolean isManager  = project.isManager(current.getId());
        if (!isAssignee && !isManager) {
            throw new SecurityException("Только исполнитель или менеджер могут прикреплять файлы");
        }
    }

    @Transactional(readOnly = true)
    public List<TaskAttachment> getAttachments(Long taskId) {
        Task task = taskRepo.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Задача не найдена"));
        checkTaskAccess(task);
        return attachmentRepo.findByTaskId(taskId);
    }

    @Transactional
    public TaskAttachment upload(Long taskId, MultipartFile file) throws IOException {
        Task task = taskRepo.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Задача не найдена"));
        checkUploadAccess(task);

        if (file.getSize() > MAX_FILE_SIZE) {
            throw new IllegalArgumentException(
                "Файл слишком большой. Максимальный размер: 10 МБ");
        }
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Файл пустой");
        }
        long count = attachmentRepo.countByTaskId(taskId);
        if (count >= MAX_FILES_PER_TASK) {
            throw new IllegalArgumentException(
                "Достигнут лимит файлов. Максимум " + MAX_FILES_PER_TASK + " файлов на задачу");
        }

        Path taskDir = Paths.get(uploadDir, "tasks", taskId.toString());
        Files.createDirectories(taskDir);

        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.isBlank()) originalName = "file";
        String storedName = UUID.randomUUID() + "_" + originalName;
        Path filePath = taskDir.resolve(storedName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);

        TaskAttachment attachment = new TaskAttachment();
        attachment.setTaskId(taskId);
        attachment.setFileName(originalName);
        attachment.setFileSize(file.getSize());
        attachment.setContentType(
            file.getContentType() != null ? file.getContentType() : "application/octet-stream");
        attachment.setFilePath(filePath.toString());
        return attachmentRepo.save(attachment);
    }

    public Resource download(Long attachmentId) throws IOException {
        TaskAttachment attachment = attachmentRepo.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Файл не найден"));
        Path path = Paths.get(attachment.getFilePath());
        Resource resource = new UrlResource(path.toUri());
        if (!resource.exists()) throw new RuntimeException("Файл не найден на диске");
        return resource;
    }

    public String getFileName(Long attachmentId) {
        return attachmentRepo.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Файл не найден"))
                .getFileName();
    }

    @Transactional
    public void deleteAttachment(Long attachmentId) {
        TaskAttachment attachment = attachmentRepo.findById(attachmentId)
                .orElseThrow(() -> new RuntimeException("Файл не найден"));
        try { Files.deleteIfExists(Paths.get(attachment.getFilePath())); } catch (IOException ignored) {}
        attachmentRepo.deleteById(attachmentId);
    }

    @Transactional
    public void deleteAllByTaskId(Long taskId) {
        List<TaskAttachment> attachments = attachmentRepo.findByTaskId(taskId);
        for (TaskAttachment a : attachments) {
            try { Files.deleteIfExists(Paths.get(a.getFilePath())); } catch (IOException ignored) {}
        }
        attachmentRepo.deleteByTaskId(taskId);
    }
}
