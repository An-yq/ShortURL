package com.project.shortlink.admin.service.impl;


import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.project.shortlink.admin.dao.entity.GroupDO;
import com.project.shortlink.admin.dao.mapper.GroupMapper;
import com.project.shortlink.admin.service.GroupService;
import org.springframework.stereotype.Service;

@Service
public class GroupServiceImpl extends ServiceImpl<GroupMapper, GroupDO> implements GroupService {

}
