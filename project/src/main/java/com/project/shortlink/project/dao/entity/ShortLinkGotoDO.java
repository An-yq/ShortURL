package com.project.shortlink.project.dao.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 短链接跳转持久层实体
 */
@Data
@TableName("t_link_goto")
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ShortLinkGotoDO {
    /**
     * id
     */
    private Long id;
    /**
     * 完整短链接
     */
    private String fullShortUrl;
    /**
     * 分组id
     */
    private String gid;
}
