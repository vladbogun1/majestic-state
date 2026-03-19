package com.majesticstate.bot.service;

import java.util.ArrayList;
import java.util.List;

public class ReportSection {
    private String title;
    private List<String> roleIds = new ArrayList<>();
    private List<String> excludeRoleIds = new ArrayList<>();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getRoleIds() {
        return roleIds;
    }

    public void setRoleIds(List<String> roleIds) {
        this.roleIds = roleIds;
    }

    public List<String> getExcludeRoleIds() {
        return excludeRoleIds;
    }

    public void setExcludeRoleIds(List<String> excludeRoleIds) {
        this.excludeRoleIds = excludeRoleIds;
    }
}
