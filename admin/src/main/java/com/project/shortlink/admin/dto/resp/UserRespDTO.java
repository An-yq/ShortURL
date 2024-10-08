package com.project.shortlink.admin.dto.resp;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.project.shortlink.admin.serialize.PhoneDesensitizationSerializer;
import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 前端响应用户实体类
 */
@Data
@Accessors(chain = true)
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
    @JsonSerialize(using = PhoneDesensitizationSerializer.class)
    private String phone;

    /**
     * 邮箱
     */
    private String mail;

}
