package com.project.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.project.shortlink.project.common.database.BaseDO;
import lombok.Data;

import java.util.Date;

/**
 * 短链接持久层实体
 */
@Data
@TableName("t_link")
public class ShortLinkDO extends BaseDO {
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
    @TableField("`describe`")
    private String describe;
}
