package com.talent.server.entity;

import com.talent.server.enums.AccountStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

@Entity
@Table(name = "registered_accounts", uniqueConstraints = @UniqueConstraint(name = "uq_registered_accounts_username", columnNames = "username"))
public class RegisteredAccount {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, length = 40)
    private String username;
    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private AccountStatus status;
    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;
    @Column(name = "reviewed_at")
    private Instant reviewedAt;
    @Column(name = "reviewed_by", length = 80)
    private String reviewedBy;

    protected RegisteredAccount() {}

    public RegisteredAccount(String username, String displayName, String passwordHash, Instant requestedAt) {
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.requestedAt = requestedAt;
        this.status = AccountStatus.PENDING_APPROVAL;
    }

    public void review(AccountStatus status, String reviewer, Instant at) {
        this.status = status;
        this.reviewedBy = reviewer;
        this.reviewedAt = at;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public AccountStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getReviewedAt() { return reviewedAt; }
    public String getReviewedBy() { return reviewedBy; }
}
