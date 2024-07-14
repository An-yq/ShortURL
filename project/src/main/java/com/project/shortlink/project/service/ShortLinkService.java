package com.project.shortlink.project.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.project.shortlink.project.dao.entity.ShortLinkDO;
import org.springframework.stereotype.Service;

/**
 * ShortLink的Service接口层
 */
@Service
public interface ShortLinkService extends IService<ShortLinkDO> {
}
