package com.majesticstate.bot.service;

import com.majesticstate.bot.domain.HrChannelSettings;
import com.majesticstate.bot.repository.HrChannelSettingsRepository;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HrChannelSettingsService {
    private final HrChannelSettingsRepository repository;

    public HrChannelSettingsService(HrChannelSettingsRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public Optional<HrChannelSettings> findByGuildId(String guildId) {
        return repository.findById(guildId);
    }

    @Transactional
    public HrChannelSettings save(String guildId,
                                  String hireChannelId,
                                  String fireChannelId,
                                  String promoteChannelId,
                                  String demoteChannelId,
                                  String promotionRequestChannelId) {
        HrChannelSettings settings = repository.findById(guildId).orElseGet(() -> {
            HrChannelSettings created = new HrChannelSettings();
            created.setGuildId(guildId);
            return created;
        });
        settings.setHireChannelId(hireChannelId);
        settings.setFireChannelId(fireChannelId);
        settings.setPromoteChannelId(promoteChannelId);
        settings.setDemoteChannelId(demoteChannelId);
        settings.setPromotionRequestChannelId(promotionRequestChannelId);
        settings.setUpdatedAt(Instant.now());
        return repository.save(settings);
    }

    public String resolveChannelId(HrChannelSettings settings, HrActionType actionType) {
        return switch (actionType) {
            case HIRE -> settings.getHireChannelId();
            case FIRE -> settings.getFireChannelId();
            case PROMOTE -> settings.getPromoteChannelId();
            case DEMOTE -> settings.getDemoteChannelId();
        };
    }

    public String resolvePromotionRequestChannelId(HrChannelSettings settings) {
        if (settings.getPromotionRequestChannelId() != null && !settings.getPromotionRequestChannelId().isBlank()) {
            return settings.getPromotionRequestChannelId();
        }
        return settings.getPromoteChannelId();
    }
}
