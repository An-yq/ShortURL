package com.project.shortlink.admin.remote.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.project.shortlink.admin.common.biz.user.UserContext;
import com.project.shortlink.admin.common.convention.exception.ClientException;
import com.project.shortlink.admin.common.convention.result.Result;
import com.project.shortlink.admin.dao.entity.GroupDO;
import com.project.shortlink.admin.dao.mapper.GroupMapper;
import com.project.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.project.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.project.shortlink.admin.remote.service.RecycleBinService;
import com.project.shortlink.admin.remote.service.ShortLinkRemoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 回收站管理实现层
 */
@Service
@RequiredArgsConstructor
public class RecycleBinServiceImpl implements RecycleBinService {
    private final GroupMapper groupMapper;

    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };
    @Override
    public Result<IPage<ShortLinkPageRespDTO>> pageRecycleBin(ShortLinkRecycleBinPageReqDTO requestParam) {
        //查询出该用户的所有分组
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getName, UserContext.getUserId())
                .eq(GroupDO::getDelFlag, 0);
        List<GroupDO> groupDOS = groupMapper.selectList(queryWrapper);
        if(CollUtil.isEmpty(groupDOS)){
            //如果没有分组
            throw new ClientException("用户暂无分组信息");
        }
        List<String> groupList = groupDOS.stream().map(GroupDO::getGid).toList();
        requestParam.setGidList(groupList);
        return shortLinkRemoteService.pageRecycleBin(requestParam);
    }
}
