package com.project.shortlink.admin.remote.dto.req;

import lombok.Data;

/**
 * 添加回收站请求实体
 */
@Data
public class SaveRecycleBinReqDTO {
    /**
     * 分组id
     */
    private String gid;
    /**
     * 完整短链接
     */
    private String fullShortUrl;

}
