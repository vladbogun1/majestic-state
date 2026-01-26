package com.majesticstate.bot.web;

import com.majesticstate.bot.service.AdminService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class SessionAuthInterceptor implements HandlerInterceptor {
    public static final String ADMIN_SESSION_KEY = "ADMIN_ID";

    private final AdminService adminService;

    public SessionAuthInterceptor(AdminService adminService) {
        this.adminService = adminService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        Object adminId = request.getSession().getAttribute(ADMIN_SESSION_KEY);
        if (adminId != null) {
            return true;
        }
        if ("/api/admins".equals(request.getRequestURI()) && !adminService.hasAdmins()) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        return false;
    }
}
