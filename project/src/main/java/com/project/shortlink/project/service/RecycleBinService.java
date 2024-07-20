package com.project.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.project.shortlink.project.dao.entity.ShortLinkDO;
import com.project.shortlink.project.dto.req.DeleteRecycleBinReqDTO;
import com.project.shortlink.project.dto.req.RecoverRecycleBinReqDTO;
import com.project.shortlink.project.dto.req.SaveRecycleBinReqDTO;
import com.project.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.project.shortlink.project.dto.resp.ShortLinkPageRespDTO;

/**
 * 回收站管理接口层
 */
public interface RecycleBinService extends IService<ShortLinkDO> {

    /**
     * 保存回收站功能
     * @param requestParam 保存回收站请求参数
     */
    void saveRecycleBin(SaveRecycleBinReqDTO requestParam);


    /**
     * 分页查询回收站短链接
     * @param requestParam 分页查询回收站请求参数 - gidList
     * @return 分页结果
     */
    IPage<ShortLinkPageRespDTO> pageRecycleBin(ShortLinkRecycleBinPageReqDTO requestParam);

    /**
     * 从回收站恢复短链接
     * @param requestParam 恢复短链接请求实体
     */
    void recoverRecycleBin(RecoverRecycleBinReqDTO requestParam);

    /**
     * 从回收站彻底删除短链接
     * @param requestParam 回收站删除短链接请求实体
     */
    void deleteRecycleBin(DeleteRecycleBinReqDTO requestParam);
}
