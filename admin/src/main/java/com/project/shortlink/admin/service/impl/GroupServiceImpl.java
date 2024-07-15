package com.project.shortlink.admin.service.impl;


import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.project.shortlink.admin.common.biz.user.UserContext;
import com.project.shortlink.admin.common.convention.result.Result;
import com.project.shortlink.admin.dao.entity.GroupDO;
import com.project.shortlink.admin.dao.mapper.GroupMapper;
import com.project.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.project.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.project.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.project.shortlink.admin.remote.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.project.shortlink.admin.remote.service.ShortLinkRemoteService;
import com.project.shortlink.admin.service.GroupService;
import com.project.shortlink.admin.tooklit.RandomStringGenerator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    private ShortLinkRemoteService shortLinkRemoteService= new ShortLinkRemoteService(){
    };
    @Override
    public void save(String groupName) {
        while(true){
            String gid = RandomStringGenerator.generateRandomString();
            if(!hasGid(gid)){
                GroupDO groupDO = GroupDO.builder()
                        .gid(gid)
                        .name(groupName)
                        .sortOrder(0)
                        .username(UserContext.getUsername())
                        .build();
                baseMapper.insert(groupDO);
                break;
            }
        }
    }

    /**
     * gid是否存在
     */
    private Boolean hasGid(String gid){
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getUsername,UserContext.getUsername());
        GroupDO groupDO = baseMapper.selectOne(queryWrapper);
        return groupDO != null;
    }


    @Override
    public List<ShortLinkGroupRespDTO> listGroup() {
        LambdaQueryWrapper<GroupDO> queryWrapper = Wrappers.lambdaQuery(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getDelFlag, 0)
                .orderByDesc(GroupDO::getSortOrder, GroupDO::getUpdateTime);
        List<GroupDO> groupDOS = baseMapper.selectList(queryWrapper);
        //查询分组时要返回分组内的短链接数量 --- 这里使用了stream流实现了在一个类list里面抽取属性list
        Result<List<ShortLinkGroupCountQueryRespDTO>> listResult = shortLinkRemoteService.listGroupShortLinkCount(groupDOS.stream().map(GroupDO::getGid).toList());
        //将groupDOS中的属性映射到shortLinkGroupRespDTOList中
        List<ShortLinkGroupRespDTO> shortLinkGroupRespDTOList = BeanUtil.copyToList(groupDOS, ShortLinkGroupRespDTO.class);
        //将listResult中的shortLinkCount抽取出来放到每一个GroupDO中
        shortLinkGroupRespDTOList.forEach(each -> {
            //依旧使用stream流，过滤出gid相同的，获取到这个ShortLinkGroupCountQueryRespDTO对象
            Optional<ShortLinkGroupCountQueryRespDTO> first = listResult.getData()
                    .stream()
                    .filter(item -> Objects.equals(item.getGid(), each.getGid()))
                    .findFirst();
            first.ifPresent(item -> each.setShortLinkCount(item.getShortLinkCount()));
        });
        return shortLinkGroupRespDTOList;
    }

    @Override
    public void updateGroup(ShortLinkGroupUpdateReqDTO requestParam) {
        LambdaUpdateWrapper<GroupDO> wrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getGid, requestParam.getGid())
                .eq(GroupDO::getUsername,UserContext.getUsername())
                .eq(GroupDO::getDelFlag,0);
        GroupDO bean = BeanUtil.toBean(requestParam, GroupDO.class);
        baseMapper.update(bean, wrapper);
    }

    @Override
    public void deleteGroup(String gid) {
        LambdaUpdateWrapper<GroupDO> wrapper = Wrappers.lambdaUpdate(GroupDO.class)
                .eq(GroupDO::getUsername, UserContext.getUsername())
                .eq(GroupDO::getGid, gid)
                .eq(GroupDO::getDelFlag, 0);
        GroupDO groupDO = new GroupDO();
        groupDO.setDelFlag(1);
        baseMapper.update(groupDO,wrapper);
    }

    @Override
    public void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam) {
        requestParam.forEach(each ->{
            GroupDO groupDO = GroupDO.builder()
                    .sortOrder(each.getSortOrder())
                            .build();
            LambdaUpdateWrapper<GroupDO> wrapper = Wrappers.lambdaUpdate(GroupDO.class)
                            .eq(GroupDO::getUsername, UserContext.getUsername())
                            .eq(GroupDO::getDelFlag, 0)
                            .eq(GroupDO::getGid, each.getGid());
            baseMapper.update(groupDO,wrapper);
            }
        );
    }
}
