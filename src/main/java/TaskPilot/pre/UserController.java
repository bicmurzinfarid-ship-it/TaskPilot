package TaskPilot.pre;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
public class UserController {
    private final UserService userService;
    public UserController(UserService userService){
        this.userService = userService;
    }

    @GetMapping("/user")
    public List<User> findAllUsers(){
        return userService.findAllUsers();
    }

    @GetMapping("/user/{id}")
    public User findUserById(@PathVariable Long id){
        return userService.findUserById(id);
    }

    @PostMapping("/user")
    public User createUser(@RequestBody User user){
        return userService.createUser(user);
    }

    @PostMapping("/user/me/avatar")
    public ResponseEntity<?> uploadAvatar(@RequestParam("file") MultipartFile file) {
        try {
            userService.uploadAvatar(file);
            return ResponseEntity.ok().build();
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(java.util.Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/user/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(@PathVariable Long id) {
        byte[] data = userService.getAvatar(id);
        if (data == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .body(data);
    }
}
