package com.xxxx.clinicbookingsystem.account.repository;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {
    Optional<Account> findByUsername(String username);
    @Query(value = "SELECT a FROM Account a JOIN FETCH a.role")
    List<Account> findAllWithRole();

    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}
