package com.project.shortlink.admin.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 用户注册请求参数实体类
 */
@Data
@Accessors(chain = true)
public class UserRegisterReqDTO {
    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;
    /**
     * 密码
     */
    private String password;
    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String mail;
}
