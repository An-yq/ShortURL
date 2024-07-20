package com.project.shortlink.admin.remote.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.project.shortlink.admin.common.convention.result.Result;
import com.project.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.project.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;

/**
 * 回收站管理接口层
 */
public interface RecycleBinService {
    /**
     * 回收站分页查询功能
     * @param requestParam
     * @return
     */
    Result<IPage<ShortLinkPageRespDTO>> pageRecycleBin(ShortLinkRecycleBinPageReqDTO requestParam);
}
