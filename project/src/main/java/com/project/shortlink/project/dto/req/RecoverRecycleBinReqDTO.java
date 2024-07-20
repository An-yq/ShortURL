package com.project.shortlink.project.dto.req;

import lombok.Data;

/**
 * 短链接从回收站恢复请求实体
 */
@Data
public class RecoverRecycleBinReqDTO {
    /**
     * 分组标识
     */
    private String gid;
    /**
     * 完整短链接
     */
    private String fullShortUrl;
}
