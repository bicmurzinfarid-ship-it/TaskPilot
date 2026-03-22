package TaskPilot.pre;

import org.springframework.web.bind.annotation.*;

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
}
