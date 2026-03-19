package com.majesticstate.bot.repository;

import com.majesticstate.bot.domain.HrChannelSettings;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HrChannelSettingsRepository extends JpaRepository<HrChannelSettings, String> {
}
