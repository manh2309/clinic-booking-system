package com.xxxx.clinicbookingsystem.account.service.impl;

import com.xxxx.clinicbookingsystem.account.dto.AccountResponse;
import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.account.service.AccountService;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {
    private final AccountRepository accountRepository;
    public AccountServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public List<AccountResponse> getAllAccounts() {
        List<Account> accounts = accountRepository.findAll();

        HashMap
        return accounts.stream()
                .map(account -> new AccountResponse(
                        account.getId(),
                        account.getUsername(),
                        account.getEmail(),
                        account.getRole().getRoleName()
                ))
                .toList();
    }
}
