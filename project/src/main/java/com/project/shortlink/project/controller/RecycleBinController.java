package com.project.shortlink.project.controller;


import com.baomidou.mybatisplus.core.metadata.IPage;
import com.project.shortlink.project.common.convention.result.Result;
import com.project.shortlink.project.common.convention.result.Results;
import com.project.shortlink.project.dto.req.SaveRecycleBinReqDTO;
import com.project.shortlink.project.dto.req.ShortLinkRecycleBinPageReqDTO;
import com.project.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.project.shortlink.project.service.RecycleBinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 短链接回收站管理控制层
 */
@RestController
@RequiredArgsConstructor
public class RecycleBinController {
    private final RecycleBinService recycleBinService;
    /**
     * 添加回收站
     */
    @PostMapping("api/short-link/v1/recycleBin/save")
    public Result<Void> saveRecycleBin(@RequestBody SaveRecycleBinReqDTO requestParam){
        recycleBinService.saveRecycleBin(requestParam);
        return Results.success();
    }

    /**
     * 分页查询回收站短链接
     */
    @GetMapping("api/short-link/v1/recycleBin/page")
    public Result<IPage<ShortLinkPageRespDTO>> pageRecycleBin(@RequestBody ShortLinkRecycleBinPageReqDTO requestParam){
        return Results.success(recycleBinService.pageRecycleBin(requestParam));
    }
}
