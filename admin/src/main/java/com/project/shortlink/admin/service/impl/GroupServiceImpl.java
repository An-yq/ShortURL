package com.project.shortlink.admin.service.impl;


import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.project.shortlink.admin.dao.entity.GroupDO;
import com.project.shortlink.admin.dao.mapper.GroupMapper;
import com.project.shortlink.admin.service.GroupService;
import com.project.shortlink.admin.tooklit.RandomStringGenerator;
import org.springframework.stereotype.Service;

@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

    @Override
    public void save(String groupName) {
        while(true){
            String gid = RandomStringGenerator.generateRandomString();
            if(!hasGid(gid)){
                GroupDO groupDO = GroupDO.builder()
                        .gid(gid)
                        .name(groupName)
                        //TODO 传递用户名（用网关来传递）
                        .username(null)
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
                .eq(GroupDO::getGid, gid);
        GroupDO groupDO = baseMapper.selectOne(queryWrapper);
        return groupDO != null;
    }
}
