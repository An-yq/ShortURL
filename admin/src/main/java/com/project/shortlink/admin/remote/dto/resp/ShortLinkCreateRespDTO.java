package com.project.shortlink.admin.remote.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkCreateRespDTO {
    /**
     * 分组id
     */
    private String gid;
    /**
     * 原始链接
     */
    private String originUrl;
    /**
     * 完整短链接
     */
    private String fullShortUrl;

}
