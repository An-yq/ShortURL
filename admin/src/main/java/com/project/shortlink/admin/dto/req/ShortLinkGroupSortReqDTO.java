package com.project.shortlink.admin.dto.req;

import lombok.Data;

@Data
public class ShortLinkGroupSortReqDTO {
    /**
     * 分组id
     */
    private String gid;
    /**
     * 序号
     */
    private Integer sortOrder;
}
