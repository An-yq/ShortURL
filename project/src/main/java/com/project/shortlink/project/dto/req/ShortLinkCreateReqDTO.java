package com.project.shortlink.project.dto.req;

import lombok.Data;

import java.util.Date;

/**
 * 创建短链接请求实体类
 */
@Data
public class ShortLinkCreateReqDTO {
    /**
     * 原始链接
     */
    private String originUrl;
    /**
     * 启用状态 0：启用 1：未启用
     */
    private int enableStatus;

    /**
     * 创建类型 0：自定义 1：控制台
     */
    private int createType;

    /**
     * 有效期类型 0：永久有效 1：自定义有效期
     */
    private int validDateType;

    /**
     * 有效日期
     */
    private Date validDate;

    /**
     * 描述
     */
    private String describe;
}
