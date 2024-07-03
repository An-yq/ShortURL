package com.project.shortlink.admin.dto.resp;

import lombok.Data;

import java.util.Date;

/**
 * 前端响应用户实体类
 */
@Data
public class UserRespDTO {
    /**
     * 用户名
     */
    private String username;

    /**
     * 真实姓名
     */
    private String realName;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 邮箱
     */
    private String mail;

}
