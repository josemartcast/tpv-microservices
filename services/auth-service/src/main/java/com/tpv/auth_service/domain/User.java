
package com.tpv.auth_service.domain;
import jakarta.persistence.*;


@Entity
@Table(name = "users")
public class User {
     @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String role;

    @Column(nullable = false)
    private boolean active = true;

    // JPA necesita constructor vacío
    protected User() {}

    public User(String username, String passwordHash, String role) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
    }

    // getters (solo los necesarios)
    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public boolean isActive() { return active; }

    public void deactivate() {
        this.active = false;
    }
}
