package com.project.shortlink.admin.controller;

import cn.hutool.core.bean.BeanUtil;
import com.project.shortlink.admin.common.convention.result.Result;
import com.project.shortlink.admin.common.convention.result.Results;
import com.project.shortlink.admin.dto.req.UserLoginReqDTO;
import com.project.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.project.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.project.shortlink.admin.dto.resp.UserActualRespDTO;
import com.project.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.project.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

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
    @GetMapping("/api/short-link/v1/user/{username}")
    public Result GetUserByUsername(@PathVariable String username){
        return Results.success(userService.GetUserByUsername(username));
    }

    /**
     * 查询用户真实信息
     */
    @GetMapping("/api/short-link/v1/actual/user/{username}")
    public Result GetActualUserByUsername(@PathVariable String username){
        return Results.success(BeanUtil.toBean(userService.GetUserByUsername(username).getData(), UserActualRespDTO.class));
    }
    /**
     * 查询用户名是否存在(是否可用)
     */
    @GetMapping("/api/short-link/v1/user/has-username")
    public Result hasUsername(@RequestParam String username){
        return Results.success(userService.hasUsername(username));
    }
    /**
     * 用户注册接口
     */
    @PostMapping("/api/short-link/v1/user")
    public Result<Void> register(@RequestBody UserRegisterReqDTO requestParam){
        userService.register(requestParam);
        return Results.success();
    }
    /**
     * 修改用户信息
     */
    @PutMapping("/api/short-link/v1/user")
    public Result<Void> update(@RequestBody UserUpdateReqDTO requestParam){
        userService.update(requestParam);
        return Results.success();
    }
    /**
     * 用户登录功能
     */
    @PostMapping("/api/short-link/v1/user/login")
    public Result<UserLoginRespDTO> login(@RequestBody UserLoginReqDTO requestParam){
        return Results.success(userService.login(requestParam));
    }
    /**
     * 查询用户是否登录
     */
    @GetMapping("/api/short-link/v1/user/check-login")
    public Result<Boolean> checkLogin(@RequestParam("token") String token,@RequestParam("username") String username){
        return Results.success(userService.checkLogin(token,username));
    }
}
