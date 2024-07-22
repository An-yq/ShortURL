package com.project.shortlink.project.dto.req;

import lombok.Data;

/**
 * 查看分组监控信息请求实体
 */
@Data
public class ShortLinkGroupStatsReqDTO {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 开始日期
     */
    private String startDate;

    /**
     * 结束日期
     */
    private String endDate;
}