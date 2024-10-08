package com.project.shortlink.project.dto.req;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

import java.util.Date;

/**
 * 修改短链接请求实体类
 */
@Data
public class ShortLinkUpdateReqDTO {
    /**
     * 域名
     */
    private String domain;
    /**
     * 完整短链接
     */
    private String fullShortUrl;
    /**
     * 原始链接
     */
    private String originUrl;
    /**
     * 原始分组
     */
    private String originGid;
    /**
     * 分组id
     */
    private String gid;

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
