package com.project.shortlink.project.dto.req;

import lombok.Data;

/**
 * 将短链接从回收站彻底删除请求实体
 */
@Data
public class DeleteRecycleBinReqDTO {
    /**
     * 分组id
     */
    private String gid;
    /**
     * 完整短链接
     */
    private String fullShortUrl;
}
