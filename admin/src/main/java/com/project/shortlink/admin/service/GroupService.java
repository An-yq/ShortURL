package com.project.shortlink.admin.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.project.shortlink.admin.dao.entity.GroupDO;
import com.project.shortlink.admin.dto.req.ShortLinkGroupSortReqDTO;
import com.project.shortlink.admin.dto.req.ShortLinkGroupUpdateReqDTO;
import com.project.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import org.springframework.stereotype.Service;

import java.util.List;

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

    /**
     * 查询短链接分组
     * @return
     */
    List<ShortLinkGroupRespDTO> listGroup();

    /**
     * 修改短链接分组名称
     * @param requestParam
     */
    void updateGroup(ShortLinkGroupUpdateReqDTO requestParam);

    void deleteGroup(String gid);

    void sortGroup(List<ShortLinkGroupSortReqDTO> requestParam);
}
