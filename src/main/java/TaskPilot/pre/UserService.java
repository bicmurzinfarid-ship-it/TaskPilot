package TaskPilot.pre;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {
    private final List<User> users = new ArrayList<>();
    private Long nextId = 1L;

    public List<User> findAllUsers(){
        return users;
    }
    public User findTaskById(Long id){
        for(User user: users){
            if(user.getId().equals(id)){return user;}
        }
        throw new RuntimeException("Not found");
    }
    public User createUser(User user){
        if(user.getId() != null){
            throw new IllegalArgumentException("Id should be empty");
        }
        if(user.getEmail() == null){
            throw new IllegalArgumentException("Email shouldn't be empty");
        }
        if(user.getUsername() == null){
            throw new IllegalArgumentException("Username shouldn't be empty");
        }
        if(user.getPassword() == null){
            throw new IllegalArgumentException("Password shouldn't be empty");
        }
        user.setId(nextId++);
        users.add(user);
        return user;
    }
}
