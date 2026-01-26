package com.majesticstate.bot.repository;

import com.majesticstate.bot.domain.BotLogEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BotLogRepository extends JpaRepository<BotLogEntry, Long> {
    Page<BotLogEntry> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
