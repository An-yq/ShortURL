package com.project.shortlink.project.dao.mapper;

import com.project.shortlink.project.dao.entity.LinkAccessStatsDO;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;

/**
 * 短链接基础访问监控持久层
 */
public interface LinkAccessStatsMapper {

    /**
     * 短链接状态变化insert语句
     * 1. 如果表中没有记录，则新建一条记录，初始值为linkAccessStatsDO的属性值
     * 2. 如果表中有记录，那么就更新原有记录的pv,uv,uip
     * @param linkAccessStatsDO 短链接基础访问实体
     */
    @Insert("""      
            INSERT INTO t_link_access_stats (full_short_url, gid, date, pv, uv, uip, hour, weekday, create_time, update_time, del_flag)
            VALUES( #{linkAccessStats.fullShortUrl}, #{linkAccessStats.gid}, #{linkAccessStats.date}, #{linkAccessStats.pv}, #{linkAccessStats.uv}, #{linkAccessStats.uip}, #{linkAccessStats.hour}, #{linkAccessStats.weekday}, NOW(), NOW(), 0) 
            ON DUPLICATE KEY
            UPDATE pv = pv +  #{linkAccessStats.pv}, 
                        uv = uv + #{linkAccessStats.uv}, 
                        uip = uip + #{linkAccessStats.uip};
        """)
    void shortLinkStats(@Param("linkAccessStats") LinkAccessStatsDO linkAccessStatsDO);
}
