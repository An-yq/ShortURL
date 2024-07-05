package com.project.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.project.shortlink.admin.common.convention.result.Result;
import com.project.shortlink.admin.dao.entity.UserDO;
import com.project.shortlink.admin.dto.req.UserLoginReqDTO;
import com.project.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.project.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.project.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.project.shortlink.admin.dto.resp.UserRespDTO;
import org.springframework.stereotype.Service;

/**
 * 用户管理接口层
 */
@Service
public interface UserService extends IService<UserDO> {
    /**
     * 根据用户name查询用户信息
     * @param username 用户名
     * @return 前端响应实体类
     */
    Result<UserRespDTO> GetUserByUsername(String username);

    /**
     * 判断用户名是可用
     * @param username 用户名
     * @return 是否可用
     */
    Boolean hasUsername(String username);

    /**
     * 注册用户
     * @param requestParam 注册用户请求实体
     */
    void register(UserRegisterReqDTO requestParam);

    /**
     * 修改用户
     * @param requestParam 修改用户请求实体
     */
    void update(UserUpdateReqDTO requestParam);

    /**
     * 用户登录
     * @param requestParam 用户登录请求实体
     * @return 登录是否成功
     */
    UserLoginRespDTO login(UserLoginReqDTO requestParam);

    Boolean checkLogin(String token,String username);

    /**
     * 用户退出登录
     * @param token token值
     * @param username 用户名
     */
    void logout(String token, String username);
}
