package com.project.shortlink.project.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 创建短链接请求实体类
 */
@Data
public class ShortLinkCreateReqDTO {
    /**
     * 域名
     */
    private String domain;
    /**
     * 原始链接
     */
    private String originUrl;
    /**
     * 分组id
     */
    private String gid;

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
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss",timezone = "GMT-8")
    private Date validDate;

    /**
     * 描述
     */
    private String describe;
}
