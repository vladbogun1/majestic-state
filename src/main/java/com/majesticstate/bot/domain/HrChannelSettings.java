package com.majesticstate.bot.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "hr_channel_settings")
public class HrChannelSettings {
    @Id
    @Column(name = "guild_id", nullable = false, length = 32)
    private String guildId;

    @Column(name = "hire_channel_id", length = 32)
    private String hireChannelId;

    @Column(name = "fire_channel_id", length = 32)
    private String fireChannelId;

    @Column(name = "promote_channel_id", length = 32)
    private String promoteChannelId;

    @Column(name = "demote_channel_id", length = 32)
    private String demoteChannelId;

    @Column(name = "promotion_request_channel_id", length = 32)
    private String promotionRequestChannelId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    public String getGuildId() {
        return guildId;
    }

    public void setGuildId(String guildId) {
        this.guildId = guildId;
    }

    public String getHireChannelId() {
        return hireChannelId;
    }

    public void setHireChannelId(String hireChannelId) {
        this.hireChannelId = hireChannelId;
    }

    public String getFireChannelId() {
        return fireChannelId;
    }

    public void setFireChannelId(String fireChannelId) {
        this.fireChannelId = fireChannelId;
    }

    public String getPromoteChannelId() {
        return promoteChannelId;
    }

    public void setPromoteChannelId(String promoteChannelId) {
        this.promoteChannelId = promoteChannelId;
    }

    public String getDemoteChannelId() {
        return demoteChannelId;
    }

    public void setDemoteChannelId(String demoteChannelId) {
        this.demoteChannelId = demoteChannelId;
    }

    public String getPromotionRequestChannelId() {
        return promotionRequestChannelId;
    }

    public void setPromotionRequestChannelId(String promotionRequestChannelId) {
        this.promotionRequestChannelId = promotionRequestChannelId;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
