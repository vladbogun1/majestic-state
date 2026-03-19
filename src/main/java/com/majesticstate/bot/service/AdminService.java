package com.majesticstate.bot.service;

import com.majesticstate.bot.domain.AdminUser;
import com.majesticstate.bot.repository.AdminUserRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminService {
    private final AdminUserRepository repository;
    private final PasswordService passwordService;

    public AdminService(AdminUserRepository repository, PasswordService passwordService) {
        this.repository = repository;
        this.passwordService = passwordService;
    }

    public boolean hasAdmins() {
        return repository.count() > 0;
    }

    public Optional<AdminUser> findByUsername(String username) {
        return repository.findByUsernameIgnoreCase(username);
    }

    public Optional<AdminUser> findById(Long id) {
        return repository.findById(id);
    }

    public List<AdminUser> listAdmins() {
        return repository.findAll();
    }

    public boolean hasPrimaryAdmins() {
        return repository.countByPrimaryAdminTrue() > 0;
    }

    @Transactional
    public AdminUser createAdmin(String username, String password) {
        String salt = passwordService.generateSalt();
        String hash = passwordService.hashPassword(password, salt);
        AdminUser adminUser = new AdminUser();
        adminUser.setUsername(username);
        adminUser.setPasswordSalt(salt);
        adminUser.setPasswordHash(hash);
        if (repository.count() == 0) {
            adminUser.setPrimaryAdmin(true);
        }
        return repository.save(adminUser);
    }

    @Transactional
    public void changePassword(AdminUser adminUser, String newPassword) {
        String salt = passwordService.generateSalt();
        String hash = passwordService.hashPassword(newPassword, salt);
        adminUser.setPasswordSalt(salt);
        adminUser.setPasswordHash(hash);
        repository.save(adminUser);
    }

    @Transactional
    public void setPrimary(AdminUser adminUser, boolean primary) {
        adminUser.setPrimaryAdmin(primary);
        repository.save(adminUser);
    }
}
