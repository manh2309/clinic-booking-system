package com.xxxx.clinicbookingsystem.auth.security;

import com.xxxx.clinicbookingsystem.account.entity.Account;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class CustomUserDetails implements UserDetails {
    private final Long accountId;
    private final String username;
    private final String password;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;

    public CustomUserDetails(Account account) {
        this.accountId = account.getId();
        this.username = account.getUsername();
        this.password = account.getPassword();
        this.enabled = Boolean.TRUE.equals(account.getIsActive());

        String roleName = account.getRole()
                .getRoleName()
                .toUpperCase(Locale.ROOT);

        String authority = roleName.startsWith("ROLE_")
                ? roleName
                : "ROLE_" + roleName;

        this.authorities = List.of(
                new SimpleGrantedAuthority(authority)
        );
    }

    public Long getAccountId() {
        return accountId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return username;
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
        return enabled;
    }
}
