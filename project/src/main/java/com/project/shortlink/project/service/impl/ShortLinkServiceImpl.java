package com.project.shortlink.project.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.project.shortlink.project.dao.entity.ShortLinkDO;
import com.project.shortlink.project.dao.mapper.ShortLinkMapper;
import com.project.shortlink.project.service.ShortLinkService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * ShortLink管理模块Service实现层
 */
@Service
@Slf4j
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {

}
