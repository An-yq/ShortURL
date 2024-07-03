package com.project.shortlink.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import com.project.shortlink.admin.common.convention.result.Result;
import com.project.shortlink.admin.common.convention.result.Results;
import com.project.shortlink.admin.dto.resp.UserActualRespDTO;
import com.project.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户管理模块
 */
@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 通过用户名查询用户的脱敏后的信息
     */
    @GetMapping("/api/shortlink/v1/user/{username}")
    public Result GetUserByUsername(@PathVariable String username){
        return Results.success(userService.GetUserByUsername(username));
    }

    /**
     * 查询用户真实信息
     */
    @GetMapping("/api/shortlink/v1/actual/user/{username}")
    public Result GetActualUserByUsername(@PathVariable String username){
        return Results.success(BeanUtil.toBean(userService.GetUserByUsername(username).getData(), UserActualRespDTO.class));
    }
}
