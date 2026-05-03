package TaskPilot.pre;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
public class TaskAttachmentController {

    private final TaskAttachmentService attachmentService;

    public TaskAttachmentController(TaskAttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    /** Список файлов задачи */
    @GetMapping("/task/{taskId}/attachments")
    public List<TaskAttachment> getAttachments(@PathVariable Long taskId) {
        return attachmentService.getAttachments(taskId);
    }

    /** Загрузить файл к задаче (multipart/form-data, поле "file") */
    @PostMapping("/task/{taskId}/attachment")
    public TaskAttachment upload(@PathVariable Long taskId,
                                 @RequestParam("file") MultipartFile file) throws IOException {
        return attachmentService.upload(taskId, file);
    }

    /** Скачать файл */
    @GetMapping("/attachment/{id}/download")
    public ResponseEntity<Resource> download(@PathVariable Long id) throws IOException {
        Resource resource = attachmentService.download(id);
        String fileName = attachmentService.getFileName(id);
        String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + encodedName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(resource);
    }

    /** Удалить файл */
    @DeleteMapping("/attachment/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        attachmentService.deleteAttachment(id);
        return ResponseEntity.noContent().build();
    }
}
