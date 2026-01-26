package com.majesticstate.bot.repository;

import com.majesticstate.bot.domain.ReportConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReportConfigRepository extends JpaRepository<ReportConfig, Long> {
}
