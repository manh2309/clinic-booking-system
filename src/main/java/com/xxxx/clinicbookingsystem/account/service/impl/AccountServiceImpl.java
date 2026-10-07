package com.xxxx.clinicbookingsystem.account.service.impl;

import com.xxxx.clinicbookingsystem.account.dto.AccountResponse;
import com.xxxx.clinicbookingsystem.account.dto.UpdateAccountStatusRequest;
import com.xxxx.clinicbookingsystem.account.entity.Account;
import com.xxxx.clinicbookingsystem.account.mapper.AccountMapper;
import com.xxxx.clinicbookingsystem.account.repository.AccountRepository;
import com.xxxx.clinicbookingsystem.account.service.AccountService;
import com.xxxx.clinicbookingsystem.auth.security.CurrentAccount;
import com.xxxx.clinicbookingsystem.common.exception.AppException;
import com.xxxx.clinicbookingsystem.common.exception.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AccountServiceImpl implements AccountService {
    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final CurrentAccount currentAccount;
    public AccountServiceImpl(AccountRepository accountRepository, AccountMapper accountMapper, CurrentAccount currentAccount) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.currentAccount = currentAccount;
    }

    @Override
    @Transactional(readOnly = true)
    public List<AccountResponse> getAllAccounts() {
        List<Account> accounts = accountRepository.findAllWithRole();
        return accounts.stream()
                .map(accountMapper::toResponse
                )
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountResponse getAccountById(Long id) {
        Account account = accountRepository.findByIdWithRole(id).orElseThrow(() -> new AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        return accountMapper.toResponse(account);
    }

    @Override
    @Transactional
    public void updateStatus(Long id, UpdateAccountStatusRequest request) {
        Account account = accountRepository.findById(id).orElseThrow(() ->  new  AppException(ErrorCode.ACCOUNT_NOT_FOUND));
        if(id.equals(currentAccount.id()) && Boolean.FALSE.equals(request.isActive())) {
            throw new AppException(ErrorCode.CANNOT_LOCK_SELF);
        }
        account.setIsActive(request.isActive());

    }
}
