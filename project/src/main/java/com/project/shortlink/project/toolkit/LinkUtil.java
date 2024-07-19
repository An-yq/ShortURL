package com.project.shortlink.project.toolkit;

import cn.hutool.core.date.DateUnit;
import cn.hutool.core.date.DateUtil;

import java.util.Date;
import java.util.Optional;

import static com.project.shortlink.project.common.constant.ShortLinkConstant.DEFAULT_CACHE_VALID_TIME;

public class LinkUtil {
    /**
     * 获取过期时间
     */
    public static long getValidTime(Date validDate){
        return Optional.ofNullable(validDate)
                .map(each ->(DateUtil.between(new Date(),each, DateUnit.MS)))
                .orElse(DEFAULT_CACHE_VALID_TIME);
    }
}
