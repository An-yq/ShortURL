package com.project.shortlink.admin.common.biz.user;


import cn.hutool.json.JSONUtil;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.io.IOException;
import java.util.Objects;

/**
 * 用户传输过滤器
 */
@RequiredArgsConstructor
public class UserTransmitFilter implements Filter {
    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest httpServletRequest = (HttpServletRequest) servletRequest;
        String username = httpServletRequest.getHeader("username");
        String token = httpServletRequest.getHeader("token");
        String requestURI = httpServletRequest.getRequestURI();
        if (!Objects.equals(requestURI,"/api/short-link/v1/user/login")) {
            Object userInfoJsonStr = stringRedisTemplate.opsForHash().get("login_" + username, token);
            if (userInfoJsonStr != null) {
                UserInfoDTO userInfoDTO = JSONUtil.toBean(userInfoJsonStr.toString(), UserInfoDTO.class);
                UserContext.setUser(userInfoDTO);
            }
        }
        try{
            filterChain.doFilter(servletRequest,servletResponse);
        }finally {
            UserContext.removeUser();
        }
    }
}
