package com.majesticstate.bot.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.majesticstate.bot.domain.AdminUser;
import com.majesticstate.bot.domain.ReportConfig;
import com.majesticstate.bot.repository.ReportConfigRepository;
import com.majesticstate.bot.service.AdminService;
import com.majesticstate.bot.service.DiscordService;
import com.majesticstate.bot.service.PasswordService;
import com.majesticstate.bot.service.ReportFormat;
import com.majesticstate.bot.service.ReportSection;
import com.majesticstate.bot.service.ReportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Validated
public class ApiController {
    private final AdminService adminService;
    private final PasswordService passwordService;
    private final DiscordService discordService;
    private final ReportConfigRepository reportRepository;
    private final ReportService reportService;
    private final ObjectMapper objectMapper;

    public ApiController(AdminService adminService,
                         PasswordService passwordService,
                         DiscordService discordService,
                         ReportConfigRepository reportRepository,
                         ReportService reportService,
                         ObjectMapper objectMapper) {
        this.adminService = adminService;
        this.passwordService = passwordService;
        this.discordService = discordService;
        this.reportRepository = reportRepository;
        this.reportService = reportService;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/bootstrap")
    public Map<String, Object> bootstrap() {
        return Map.of("hasAdmins", adminService.hasAdmins());
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        Optional<AdminUser> adminOpt = adminService.findByUsername(request.username());
        if (adminOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }
        AdminUser admin = adminOpt.get();
        if (!passwordService.matches(request.password(), admin.getPasswordSalt(), admin.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Invalid credentials"));
        }
        servletRequest.getSession().setAttribute(SessionAuthInterceptor.ADMIN_SESSION_KEY, admin.getId());
        return ResponseEntity.ok(Map.of("username", admin.getUsername()));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest servletRequest) {
        servletRequest.getSession().invalidate();
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/session")
    public Map<String, Object> session(HttpServletRequest servletRequest) {
        Object adminId = servletRequest.getSession().getAttribute(SessionAuthInterceptor.ADMIN_SESSION_KEY);
        if (adminId == null) {
            return Map.of("authenticated", false);
        }
        return Map.of("authenticated", true, "adminId", adminId);
    }

    @GetMapping("/admins")
    public List<AdminSummary> admins() {
        return adminService.listAdmins().stream()
                .map(admin -> new AdminSummary(admin.getId(), admin.getUsername(), admin.getCreatedAt()))
                .collect(Collectors.toList());
    }

    @PostMapping("/admins")
    public ResponseEntity<?> createAdmin(@Valid @RequestBody AdminCreateRequest request) {
        if (adminService.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Username already exists"));
        }
        AdminUser created = adminService.createAdmin(request.username(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(Map.of("id", created.getId(), "username", created.getUsername()));
    }

    @PostMapping("/admins/change-password")
    public ResponseEntity<?> changePassword(@Valid @RequestBody ChangePasswordRequest request, HttpServletRequest servletRequest) {
        Object adminId = servletRequest.getSession().getAttribute(SessionAuthInterceptor.ADMIN_SESSION_KEY);
        if (adminId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        AdminUser admin = adminService.findById(Long.valueOf(adminId.toString())).orElse(null);
        if (admin == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Not authenticated"));
        }
        if (!passwordService.matches(request.currentPassword(), admin.getPasswordSalt(), admin.getPasswordHash())) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", "Current password incorrect"));
        }
        adminService.changePassword(admin, request.newPassword());
        return ResponseEntity.ok(Map.of("status", "ok"));
    }

    @GetMapping("/discord/guilds")
    public List<GuildSummary> guilds() {
        return discordService.listGuilds().stream()
                .map(guild -> new GuildSummary(guild.getId(), guild.getName()))
                .collect(Collectors.toList());
    }

    @GetMapping("/discord/guilds/{guildId}/channels")
    public List<ChannelSummary> channels(@PathVariable String guildId) {
        List<TextChannel> channels = discordService.listTextChannels(guildId);
        return channels.stream()
                .map(channel -> new ChannelSummary(channel.getId(), channel.getName()))
                .collect(Collectors.toList());
    }

    @GetMapping("/discord/guilds/{guildId}/roles")
    public List<RoleSummary> roles(@PathVariable String guildId) {
        List<Role> roles = discordService.listRoles(guildId);
        return roles.stream()
                .map(role -> new RoleSummary(role.getId(), role.getName()))
                .collect(Collectors.toList());
    }

    @GetMapping("/reports")
    public List<ReportSummary> listReports() {
        return reportRepository.findAll().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());
    }

    @PostMapping("/reports")
    public ResponseEntity<?> createReport(@Valid @RequestBody ReportRequest request) throws Exception {
        ReportConfig config = new ReportConfig();
        applyReportRequest(config, request);
        config.setCreatedAt(Instant.now());
        config.setUpdatedAt(Instant.now());
        ReportConfig saved = reportRepository.save(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(toSummary(saved));
    }

    @PutMapping("/reports/{id}")
    public ResponseEntity<?> updateReport(@PathVariable Long id, @Valid @RequestBody ReportRequest request) throws Exception {
        ReportConfig config = reportRepository.findById(id).orElse(null);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        applyReportRequest(config, request);
        config.setUpdatedAt(Instant.now());
        ReportConfig saved = reportRepository.save(config);
        return ResponseEntity.ok(toSummary(saved));
    }

    @DeleteMapping("/reports/{id}")
    public ResponseEntity<?> deleteReport(@PathVariable Long id) {
        reportRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/reports/{id}/run")
    public ResponseEntity<?> runReport(@PathVariable Long id) {
        ReportConfig config = reportRepository.findById(id).orElse(null);
        if (config == null) {
            return ResponseEntity.notFound().build();
        }
        reportService.publishReport(config);
        return ResponseEntity.ok(Map.of("status", "queued"));
    }

    private void applyReportRequest(ReportConfig config, ReportRequest request) throws Exception {
        config.setName(request.name());
        config.setGuildId(request.guildId());
        config.setChannelId(request.channelId());
        config.setIntervalMinutes(request.intervalMinutes());
        config.setEnabled(request.enabled());
        String rulesJson = objectMapper.writeValueAsString(request.sections());
        String formatJson = objectMapper.writeValueAsString(request.format());
        config.setRulesJson(rulesJson);
        config.setFormatJson(formatJson);
    }

    private ReportSummary toSummary(ReportConfig config) {
        return new ReportSummary(
                config.getId(),
                config.getName(),
                config.getGuildId(),
                config.getChannelId(),
                config.getIntervalMinutes(),
                config.isEnabled(),
                config.getRulesJson(),
                config.getFormatJson(),
                config.getLastMessageId(),
                config.getLastRunAt()
        );
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record AdminCreateRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    public record AdminSummary(Long id, String username, Instant createdAt) {
    }

    public record GuildSummary(String id, String name) {
    }

    public record ChannelSummary(String id, String name) {
    }

    public record RoleSummary(String id, String name) {
    }

    public record ReportRequest(
            @NotBlank String name,
            @NotBlank String guildId,
            @NotBlank String channelId,
            @NotNull Integer intervalMinutes,
            boolean enabled,
            @NotNull List<ReportSection> sections,
            @NotNull ReportFormat format
    ) {
    }

    public record ReportSummary(
            Long id,
            String name,
            String guildId,
            String channelId,
            Integer intervalMinutes,
            boolean enabled,
            String rulesJson,
            String formatJson,
            String lastMessageId,
            Instant lastRunAt
    ) {
    }
}
