package com.majesticstate.bot.service;

import com.majesticstate.bot.domain.BotSettings;
import com.majesticstate.bot.repository.BotSettingsRepository;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BotSettingsService {
    private static final long SETTINGS_ID = 1L;

    private final BotSettingsRepository repository;

    public BotSettingsService(BotSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BotSettings getSettings() {
        return repository.findById(SETTINGS_ID).orElseGet(() -> {
            BotSettings settings = new BotSettings();
            settings.setId(SETTINGS_ID);
            settings.setEnabled(false);
            return repository.save(settings);
        });
    }

    @Transactional
    public BotSettings updateSettings(String token, boolean enabled) {
        BotSettings settings = getSettings();
        settings.setToken(token);
        settings.setEnabled(enabled);
        settings.setUpdatedAt(Instant.now());
        return repository.save(settings);
    }

    @Transactional
    public BotSettings updateEnabled(boolean enabled) {
        BotSettings settings = getSettings();
        settings.setEnabled(enabled);
        settings.setUpdatedAt(Instant.now());
        return repository.save(settings);
    }
}
