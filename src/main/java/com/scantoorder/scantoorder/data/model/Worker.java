package com.scantoorder.scantoorder.data.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

@Data
@Entity
public class Worker implements UserDetails {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String workerId;
    @Column(unique = true)
    private String username;
    @ManyToOne(optional = true)
    @JoinColumn(name = "tenant_id", nullable = true)
    private Tenant tenant;
    private boolean mustChangePassword;
    @JsonIgnore
    private String password;
    private String fullName;
    @Enumerated(EnumType.STRING)
    private WorkerRole role;
    private boolean isActive;
    // Sign-in tokens issued before this moment no longer work: a new password signs the person out everywhere.
    private Instant passwordChangedAt;
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