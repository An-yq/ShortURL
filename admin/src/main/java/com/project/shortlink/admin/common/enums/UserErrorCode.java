package com.project.shortlink.admin.common.enums;

import com.project.shortlink.admin.common.convention.errorcode.IErrorCode;

public enum UserErrorCode implements IErrorCode {

    USER_TOKEN_FAIL("A000200","用户Token验证失败"),

    USER_NULL("B000200","用户不存在"),
    USER_NAME_EXIST("B000201","用户名已存在"),
    USER_EXIST("B000202","用户已存在"),
    USER_SAVE_FAIL("B000203","用户新增失败");
    private final String code;
    private final String message;

    UserErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
