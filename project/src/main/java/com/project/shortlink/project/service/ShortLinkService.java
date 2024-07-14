package com.project.shortlink.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.project.shortlink.project.dao.entity.ShortLinkDO;
import com.project.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.project.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
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
}
