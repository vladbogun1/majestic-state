package com.majesticstate.bot.repository;

import com.majesticstate.bot.domain.BotSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotSettingsRepository extends JpaRepository<BotSettings, Long> {
}
