package com.project.shortlink.project.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import com.project.shortlink.project.dao.entity.ShortLinkDO;
import com.project.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.project.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.project.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.project.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.project.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.project.shortlink.project.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.project.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ShortLink的Service接口层
 */
@Service
public interface ShortLinkService extends IService<ShortLinkDO> {
    /**
     * 创建短链接接口
     * @param requestParam 请求实体类
     * @return 响应实体类
     */
    ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam);

    /**
     * 分页查询短链接
     * @param requestParam 分页查询请求参数（gid）
     * @return 分页查询结果
     */

    IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam);

    /**
     * 查询分组内的短链接数量
     * @param requestParam 分组id集合
     * @return 分组id+短链接数量 集合
     */
    List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> requestParam);

    /**
     * 修改短链接信息
     * @param requestParam 修改短链接信息实体
     * @return void
     */
    void updateShortLink(ShortLinkUpdateReqDTO requestParam);

    /**
     * 短链接跳转功能
     * @param shortUri 短链接后缀
     * @param request 请求体
     * @param response 响应体
     */
    void restoreUrl(String shortUri, ServletRequest request, ServletResponse response);

    void linkAccessStats(String fullShortUrl, String gid, ShortLinkStatsRecordDTO shortLinkStatsRecordDTO);
}
