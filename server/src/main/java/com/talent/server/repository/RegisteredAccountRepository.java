package com.talent.server.repository;

import com.talent.server.entity.RegisteredAccount;
import com.talent.server.enums.AccountStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface RegisteredAccountRepository extends JpaRepository<RegisteredAccount, Long> {
    Optional<RegisteredAccount> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    List<RegisteredAccount> findAllByOrderByRequestedAtDesc();
    List<RegisteredAccount> findAllByStatusOrderByRequestedAtAsc(AccountStatus status);
}
