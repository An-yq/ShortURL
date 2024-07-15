package com.project.shortlink.admin.remote.dto.resp;

import lombok.Data;

@Data
public class ShortLinkGroupCountQueryRespDTO {
    /**
     * 分组id
     */
    private String gid;
    /**
     * 短链接数量
     */
    private Integer shortLinkCount;
}
