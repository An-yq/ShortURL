package com.project.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.project.shortlink.project.dao.entity.ShortLinkDO;
import com.project.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.project.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.project.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.project.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import org.springframework.stereotype.Service;

/**
 * ShortLink的Service接口层
 */
@Service
public interface ShortLinkService extends IService<ShortLinkDO> {
    /**
     * 创建短链接接口
     * @param requestParam 请求实体类
     * @return 响应实体类
     */
    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam);

    /**
     * 分页查询短链接
     * @param requestParam 分页查询请求参数（gid）
     * @return 分页查询结果
     */

    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam);

}
