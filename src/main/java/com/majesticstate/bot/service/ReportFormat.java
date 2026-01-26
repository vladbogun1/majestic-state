package com.majesticstate.bot.service;

public class ReportFormat {
    private boolean showRoleName = true;
    private boolean showUserId = true;
    private boolean showMention = true;

    public boolean isShowRoleName() {
        return showRoleName;
    }

    public void setShowRoleName(boolean showRoleName) {
        this.showRoleName = showRoleName;
    }

    public boolean isShowUserId() {
        return showUserId;
    }

    public void setShowUserId(boolean showUserId) {
        this.showUserId = showUserId;
    }

    public boolean isShowMention() {
        return showMention;
    }

    public void setShowMention(boolean showMention) {
        this.showMention = showMention;
    }
}
