package com.project.shortlink.admin.dto.req;

import lombok.Data;
import lombok.experimental.Accessors;

@Data
@Accessors(chain = true)
public class UserLoginReqDTO {
    /**
     * 用户名
     */
    private String username;
    /**
     * 密码
     */
    private String password;
}
