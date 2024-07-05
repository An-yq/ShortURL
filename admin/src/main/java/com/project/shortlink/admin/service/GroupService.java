package com.project.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.project.shortlink.admin.dao.entity.GroupDO;
import org.springframework.stereotype.Service;

/**
 * 短链接分组模块接口
 */
@Service
public interface GroupService extends IService<GroupDO> {
    /**
     * 新增短链接分组
     * @param groupName 分组名
     */
    void save(String groupName);
}
