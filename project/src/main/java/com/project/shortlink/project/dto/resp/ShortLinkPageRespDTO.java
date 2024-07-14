package com.project.shortlink.project.dto.resp;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Data;

import java.util.Date;

/**
 * 分页查询相响应实体
 */
@Data
public class ShortLinkPageRespDTO {
    /**
     * 域名
     */
    private String domain;

    /**
     * 短链接
     */
    private String shortUri;

    /**
     * 完整短链接
     */
    private String fullShortUrl;

    /**
     * 原始链接
     */
    private String originUrl;

    /**
     * 点击次数
     */
    private Integer clickNum;

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
    private Date validDate;

    /**
     * 描述
     */
    @TableField("`describe`")
    private String describe;
}
