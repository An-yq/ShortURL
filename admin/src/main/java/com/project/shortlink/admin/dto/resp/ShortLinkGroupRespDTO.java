package com.project.shortlink.admin.dto.resp;

import lombok.Data;
import lombok.experimental.Accessors;

/**
 * 查询短链接分组集合返回实体
 */
@Data
@Accessors(chain = true)
public class ShortLinkGroupRespDTO {

    /**
     * 分组标识
     */
    private String gid;

    /**
     * 分组名称
     */
    private String name;


    /**
     * 分组排序
     */
    private Integer sortOrder;
}
