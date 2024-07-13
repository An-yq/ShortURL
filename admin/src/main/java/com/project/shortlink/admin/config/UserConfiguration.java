package com.project.shortlink.admin.config;

import com.project.shortlink.admin.common.biz.user.UserTransmitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * 用户配置类
 * 用来配置用户信息拦截器的
 */
@Configuration
public class UserConfiguration {
    /**
     * 配置用户信息拦截器
     * 使用SpringBoot提供的FilterRegistrationBean用来注册过滤器
     */
    @Bean
    public FilterRegistrationBean<UserTransmitFilter> globalUserTransmitFilter(StringRedisTemplate stringRedisTemplate){
        FilterRegistrationBean<UserTransmitFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new UserTransmitFilter(stringRedisTemplate));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(0);
        return registrationBean;
    }
}
