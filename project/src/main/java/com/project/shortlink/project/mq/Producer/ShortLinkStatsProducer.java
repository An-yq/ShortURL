package com.project.shortlink.project.mq.Producer;

import cn.hutool.core.lang.UUID;
import com.alibaba.fastjson.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 监控信息保存生产者
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ShortLinkStatsProducer {
    private final RocketMQTemplate rocketMQTemplate;//消息生产者模版

    //引入配置文件中的topic信息
    @Value("${rocketmq.producer.topic}")
    private String statsTopic;

    //编写发送逻辑
    public void send(Map<String,String> producerMap){
        //生成一个消息key，用来后面保证幂等性
        String key = UUID.randomUUID().toString(true);
        producerMap.put("key",key);
        //打包一个消息
        Message<Map<String, String>> message = MessageBuilder.withPayload(producerMap)
                .setHeader(MessageConst.PROPERTY_KEYS, key)
                .build();
        try {
            //尝试发送消息，同步发送，最高延迟时间为2s
            SendResult sendResult = rocketMQTemplate.syncSend(statsTopic, message, 2000L);
            //打印发送消息日志
            log.info("[消息访问统计监控保存]消息的key为:{},消息的id为:{},消息发送状态:{}"
                    ,key,sendResult.getMsgId(),sendResult.getSendStatus());
        }catch (Throwable ex){
            //消息发送失败，打印日志
            log.error("[消息访问统计监控] 消息发送失败，消息体：{}", JSON.toJSONString(producerMap), ex);
        }
    }

}
