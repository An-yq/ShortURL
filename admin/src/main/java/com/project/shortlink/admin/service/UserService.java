package com.project.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.project.shortlink.admin.common.convention.result.Result;
import com.project.shortlink.admin.dao.entity.UserDO;
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
    public Result<UserRespDTO> GetUserByUsername(String username);
}
