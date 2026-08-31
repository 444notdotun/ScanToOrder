package com.scantoorder.scantoorder.data.model;

import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

@Data
@Entity
public class Worker implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String workerId;
    private String username;
    private String password;
    private String fullName;
    @Enumerated(EnumType.STRING)
    private WorkerRole role;
    private boolean isActive;
    @CreatedBy
    private String createdAt;
    @UpdateTimestamp
    private String updatedAt;

    @PrePersist
    public void prePersist(){
        isActive = true;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return isActive;
    }
}