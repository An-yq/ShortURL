package com.project.shortlink.admin.dto.req;

import lombok.Data;

/**
 * 修改分组名称请求实体
 */
@Data
public class ShortLinkGroupUpdateReqDTO {
    private String gid;
    private String name;
}
