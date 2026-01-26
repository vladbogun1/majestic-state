package com.majesticstate.bot.service;

import com.majesticstate.bot.domain.BotLogEntry;
import com.majesticstate.bot.repository.BotLogRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BotLogService {
    private final BotLogRepository repository;

    public BotLogService(BotLogRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void log(String level, String message) {
        BotLogEntry entry = new BotLogEntry();
        entry.setLevel(level);
        entry.setMessage(message);
        entry.setCreatedAt(Instant.now());
        repository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<BotLogEntry> latest(int limit) {
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, limit)).getContent();
    }
}
