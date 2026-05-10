package com.dev.codingagent.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Document(collection = "users")   // MongoDB collection name
public class User implements UserDetails {

    @Id
    private String id;             // MongoDB uses String ObjectId, not Long

    @Indexed(unique = true)        // creates a unique index on email in MongoDB
    private String email;

    private String password;

    private Role role = Role.USER;

    public enum Role { USER, ADMIN }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override public String getUsername()    { return email; }
    @Override public String getPassword()    { return password; }
    @Override public boolean isAccountNonExpired()     { return true; }
    @Override public boolean isAccountNonLocked()      { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled()               { return true; }

    public String getId()    { return id; }
    public String getEmail() { return email; }
    public Role getRole()    { return role; }

    public void setEmail(String email)       { this.email = email; }
    public void setPassword(String password) { this.password = password; }
    public void setRole(Role role)           { this.role = role; }
}