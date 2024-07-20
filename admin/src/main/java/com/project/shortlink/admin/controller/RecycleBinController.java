package com.project.shortlink.admin.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.project.shortlink.admin.common.convention.result.Result;
import com.project.shortlink.admin.remote.dto.req.SaveRecycleBinReqDTO;
import com.project.shortlink.admin.remote.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.project.shortlink.admin.remote.dto.resp.ShortLinkPageRespDTO;
import com.project.shortlink.admin.remote.service.RecycleBinService;
import com.project.shortlink.admin.remote.service.ShortLinkRemoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 回收站管理控制层
 */

@RestController
@RequiredArgsConstructor
public class RecycleBinController {
    //后续改成feign调用
    ShortLinkRemoteService shortLinkRemoteService = new ShortLinkRemoteService() {
    };
    private final RecycleBinService recycleBinService;
    /**
     * 保存回收站功能
     */
    @PostMapping("/api/short-link/admin/v1/recycleBin/save")
    public void saveRecycleBin(@RequestBody SaveRecycleBinReqDTO requestParam){
        shortLinkRemoteService.saveRecycleBin(requestParam);
    }
    /**
     * 分页查询回收站短链接
     * 1. 在这个里面先调用回收站Service的方法
     * 2. Service的方法会计算出该用户的所有gid，然后调用remoteService的方法
     */
    @GetMapping("/api/short-link/admin/v1/recycleBin/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageRecycleBin(ShortLinkRecycleBinPageReqDTO requestParam){
        return recycleBinService.pageRecycleBin(requestParam);
    }
}
