package com.xxxx.clinicbookingsystem.account.repository;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.id = :id")
    Optional<Account> lockById(Long id);

    Optional<Account> findByUsername(String username);
    @Query(value = "SELECT a FROM Account a JOIN FETCH a.role")
    List<Account> findAllWithRole();

    @Query("SELECT a FROM Account a JOIN FETCH a.role WHERE a.id = :id")
    Optional<Account> findByIdWithRole(Long id);

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByEmailAndIdNot(String email, Long id);
}
