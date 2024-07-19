package com.project.shortlink.project.common.constant;

/**
 * redis缓存常量类
 */
public class RedisConstant {
    /**
     * 跳转短链接前缀key
     * %s代表原始链接
     */
    public static final String GOTO_SHORT_LINK_KEY = "short-link_goto_%s";
    /**
     * 跳转短链接锁前缀key
     */
    public static final String LOCK_GOTO_SHORT_LINK_KEY = "short-link_lock_goto_%s";
    /**
     * 跳转短链接是否为空(字段不为空，表示短链接不可用)
     */
    public static final String GOTO_SHORT_LINK_IS_NULL_KEY = "short-link_goto_is_null_%s";
}
