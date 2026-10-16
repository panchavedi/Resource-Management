package com.talent.server.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/** Persistent grant of manager access to an account configured by the operator. */
@Entity
@Table(name = "manager_access_grants", uniqueConstraints = @UniqueConstraint(name = "uq_manager_access_username", columnNames = "username"))
public class ManagerAccessGrant {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 80)
    private String username;
    @Column(nullable = false, length = 80)
    private String grantedBy;
    @Column(nullable = false)
    private Instant grantedAt;

    protected ManagerAccessGrant() {}

    public ManagerAccessGrant(String username, String grantedBy, Instant grantedAt) {
        this.username = username;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
    }

    public String getUsername() { return username; }
}
