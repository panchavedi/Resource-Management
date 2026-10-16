package com.talent.server.repository;

import com.talent.server.entity.ManagerAccessGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ManagerAccessGrantRepository extends JpaRepository<ManagerAccessGrant, Long> {
    Optional<ManagerAccessGrant> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    void deleteByUsernameIgnoreCase(String username);
}
