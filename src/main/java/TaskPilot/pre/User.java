package TaskPilot.pre;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "avatar_path", columnDefinition = "varchar(512)")
    private String avatarPath;

    public User() {}
    public User(Long id, String username, String password, String email){
        this.id = id;
        this.username = username;
        this.password = password;
        this.email = email;
    }
    public Long getId(){return id;}
    public String getUsername(){return username;}
    public String getPassword(){return password;}
    public String getEmail(){return email;}
    public String getAvatarPath(){return avatarPath;}

    public void setId(Long id) { this.id = id; }
    public void setUsername(String username) { this.username = username; }
    public void setPassword(String password){ this.password = password; }
    public void setEmail(String email) { this.email = email; }
    public void setAvatarPath(String avatarPath) { this.avatarPath = avatarPath; }
}
