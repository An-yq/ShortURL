package com.project.shortlink.admin.controller;

import com.project.shortlink.admin.common.convention.result.Result;
import com.project.shortlink.admin.common.convention.result.Results;
import com.project.shortlink.admin.dto.req.ShortLinkGroupSaveReqDTO;
import com.project.shortlink.admin.dto.req.UpdateGroupReqDTO;
import com.project.shortlink.admin.dto.resp.ShortLinkGroupRespDTO;
import com.project.shortlink.admin.service.GroupService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class GroupController {
    private final GroupService groupService;

    /**
     * 新增短链接分组
     */
    @PostMapping("/api/short-link/v1/group")
    public Result<Void> save(@RequestBody ShortLinkGroupSaveReqDTO requestParam){
        groupService.save(requestParam.getName());
        return Results.success();
    }

    /**
     * 查询分组集合
     */
    @GetMapping("/api/short-link/v1/group")
    public Result<List<ShortLinkGroupRespDTO>> listGroup(){
        return Results.success(groupService.listGroup());
    }

    /**
     * 修改短链接分组名称
     */
    @PutMapping("/api/short-link/v1/group")
    public Result<Void> updateGroup(@RequestBody UpdateGroupReqDTO requestParam){
        groupService.updateGroup(requestParam);
        return Results.success();
    }
}
