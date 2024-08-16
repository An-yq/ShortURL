package com.project.shortlink.project.mq.idempotent;

import cn.hutool.core.util.StrUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

/**
 * 消息幂等处理类
 */
@Component
@RequiredArgsConstructor
public class MessageQueueIdempotentHandler {
    private final StringRedisTemplate stringRedisTemplate;
    private static final String IDEMPOTENT_KEY_PREFIX = "short-link:idempotent:";

    /**
     * 判断消息是否被消费过,消费过返回true，没消费过返回false
     * 没消费过奖消息放进redis，设置两分钟过期时间
     */
    public boolean isMessageProcessed(String messageKey){
        String key = IDEMPOTENT_KEY_PREFIX + messageKey;
        //如果成功添加了，说明消息还没有被消费过
        return Boolean.FALSE.equals(stringRedisTemplate.opsForValue().setIfAbsent(key, "0", 2, TimeUnit.MINUTES));
    }

    /**
     * 消息消费成功处理方法
     */
    public void setAccomplish(String messageKey){
        //在redis中获取当前key的消息，并将消息置为1(已消费)
        String key = IDEMPOTENT_KEY_PREFIX + messageKey;
        stringRedisTemplate.opsForValue().set(key, "1", 2, TimeUnit.MINUTES);
    }
    /**
     * 判断消息有没有消费成功
     */
    public boolean isAccomplish(String messageKey){
        String key = IDEMPOTENT_KEY_PREFIX + messageKey;
        String res = stringRedisTemplate.opsForValue().get(key);
        if(StrUtil.isNotBlank(res)){
            //防御式编程
            if(res.equals("1")){
                return true;
            }
        }
        return false;
    }
}
