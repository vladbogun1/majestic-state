package com.majesticstate.bot.web;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    @GetMapping("/")
    public String root(HttpServletRequest request) {
        Object adminId = request.getSession().getAttribute(SessionAuthInterceptor.ADMIN_SESSION_KEY);
        if (adminId != null) {
            return "forward:/app/index.html";
        }
        return "forward:/login.html";
    }
}
