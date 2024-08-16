package com.project.shortlink.project.mq.Consumer;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.project.shortlink.project.common.convention.exception.ServiceException;
import com.project.shortlink.project.dao.entity.*;
import com.project.shortlink.project.dao.mapper.*;
import com.project.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.project.shortlink.project.mq.idempotent.MessageQueueIdempotentHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.project.shortlink.project.common.constant.RedisConstant.LOCK_GID_UPDATE_KEY;
import static com.project.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;

/**
 * 保存监控信息消息消费者
 */
@Slf4j
@Component
@RequiredArgsConstructor
//注解添加消息监听器
@RocketMQMessageListener(
        consumerGroup = "${rocketmq.consumer.group}",
        topic = "${rocketmq.producer.topic}"
)
public class ShortLinkStatsConsumer implements RocketMQListener<Map<String, String>> {


    //高德地图api
    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;

    private final ShortLinkMapper shortLinkMapper;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final RedissonClient redissonClient;
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;
    private final MessageQueueIdempotentHandler messageQueueIdempotentHandler;


    /**
     * 消息消费逻辑
     */
    public void onMessage(Map<String, String> producerMap) {
        //获取消息的key
        String key = producerMap.get("key");
        // 将消息放入redis，看能不能放成功，放成功，说明这是新消息
        // 没有被消费，放失败了，说明已经有这个消息了（即返回true，说明消息已经被消费过）
        if(messageQueueIdempotentHandler.isMessageProcessed(key)){
            //消息被消费过了，判断有没有消费成功
            if(messageQueueIdempotentHandler.isAccomplish(key)){
                //已经被成功消费了，不用管了
                return;
            }
            //没有成功消费，抛出异常
            throw new ServiceException("消息未完成流程，需要消息重试");
        }
        //开始消费消息
        try {
            String fullShortUrl = producerMap.get("fullShortUrl");
            if (StrUtil.isNotBlank(fullShortUrl)) {
                String gid = producerMap.get("gid");
                ShortLinkStatsRecordDTO statsRecord = JSON.parseObject(producerMap.get("statsRecord"), ShortLinkStatsRecordDTO.class);
                actualLinkStatsSave(fullShortUrl, gid, statsRecord);
            }
        } catch (Throwable ex) {
            log.error("记录短链接监控消费异常", ex);
            throw ex;
        }
        try {
            messageQueueIdempotentHandler.setAccomplish(key);
        } catch (Throwable ex) {
            log.error("短链接添加幂等标识异常", ex);
            throw ex;
        }
    }

    /**
     * 真正将监控信息保存
     *
     * @param fullShortUrl
     * @param gid
     */
    private void actualLinkStatsSave(String fullShortUrl, String gid, ShortLinkStatsRecordDTO statsRecord) {
        // 如果 fullShortUrl 为 null 或空，使用 statsRecord 中的 fullShortUrl
        fullShortUrl = Optional.ofNullable(fullShortUrl).orElse(statsRecord.getFullShortUrl());

        // 获取针对 fullShortUrl 的读写锁
        RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, fullShortUrl));
        RLock rLock = readWriteLock.readLock();

        // 尝试获取读锁，防止并发访问问题
        rLock.lock();
        try {
            // 如果 gid 为空，从数据库中查询 shortLinkGotoDO 对象获取 gid
            if (StrUtil.isBlank(gid)) {
                LambdaQueryWrapper<ShortLinkGotoDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                        .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
                ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryWrapper);
                gid = shortLinkGotoDO.getGid();
            }

            // 获取当前小时和星期几的值
            int hour = DateUtil.hour(new Date(), true);
            Week week = DateUtil.dayOfWeekEnum(new Date());
            int weekValue = week.getIso8601Value();

            // 创建 LinkAccessStatsDO 对象，用于记录短链接的访问统计数据
            LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .pv(1) // 页面访问量 PV
                    .uv(statsRecord.getUvFirstFlag() ? 1 : 0) // 独立访客 UV
                    .uip(statsRecord.getUipFirstFlag() ? 1 : 0) // 唯一 IP UIP
                    .hour(hour) // 当前小时
                    .weekday(weekValue) // 当前星期几
                    .fullShortUrl(fullShortUrl) // 短链接的完整 URL
                    .gid(gid) // 短链接的唯一标识
                    .date(new Date()) // 当前日期
                    .build();

            // 保存短链接的访问统计数据到数据库
            linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);

            // 获取地理位置信息
            Map<String, Object> localeParamMap = new HashMap<>();
            localeParamMap.put("key", statsLocaleAmapKey);
            localeParamMap.put("ip", statsRecord.getRemoteAddr());
            String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap);
            JSONObject localeResultObj = JSON.parseObject(localeResultStr);
            String infoCode = localeResultObj.getString("infocode");
            String actualProvince = "未知";
            String actualCity = "未知";

            // 如果获取位置信息成功（infoCode 为 "10000"），则处理并保存位置信息
            if (StrUtil.isNotBlank(infoCode) && StrUtil.equals(infoCode, "10000")) {
                String province = localeResultObj.getString("province");
                boolean unknownFlag = StrUtil.equals(province, "[]");
                LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                        .province(actualProvince = unknownFlag ? actualProvince : province)
                        .city(actualCity = unknownFlag ? actualCity : localeResultObj.getString("city"))
                        .adcode(unknownFlag ? "未知" : localeResultObj.getString("adcode"))
                        .cnt(1) // 计数
                        .fullShortUrl(fullShortUrl) // 短链接的完整 URL
                        .country("中国") // 国家
                        .gid(gid) // 短链接的唯一标识
                        .date(new Date()) // 当前日期
                        .build();
                linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);
            }

            // 创建并保存操作系统的统计数据
            LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                    .os(statsRecord.getOs()) // 操作系统
                    .cnt(1) // 计数
                    .gid(gid) // 短链接的唯一标识
                    .fullShortUrl(fullShortUrl) // 短链接的完整 URL
                    .date(new Date()) // 当前日期
                    .build();
            linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);

            // 创建并保存浏览器的统计数据
            LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                    .browser(statsRecord.getBrowser()) // 浏览器
                    .cnt(1) // 计数
                    .gid(gid) // 短链接的唯一标识
                    .fullShortUrl(fullShortUrl) // 短链接的完整 URL
                    .date(new Date()) // 当前日期
                    .build();
            linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);

            // 创建并保存设备的统计数据
            LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                    .device(statsRecord.getDevice()) // 设备
                    .cnt(1) // 计数
                    .gid(gid) // 短链接的唯一标识
                    .fullShortUrl(fullShortUrl) // 短链接的完整 URL
                    .date(new Date()) // 当前日期
                    .build();
            linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);

            // 创建并保存网络的统计数据
            LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                    .network(statsRecord.getNetwork()) // 网络
                    .cnt(1) // 计数
                    .gid(gid) // 短链接的唯一标识
                    .fullShortUrl(fullShortUrl) // 短链接的完整 URL
                    .date(new Date()) // 当前日期
                    .build();
            linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);

            // 创建并保存访问日志
            LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                    .user(statsRecord.getUv()) // 用户标识
                    .ip(statsRecord.getRemoteAddr()) // IP 地址
                    .browser(statsRecord.getBrowser()) // 浏览器
                    .os(statsRecord.getOs()) // 操作系统
                    .network(statsRecord.getNetwork()) // 网络
                    .device(statsRecord.getDevice()) // 设备
                    .locale(StrUtil.join("-", "中国", actualProvince, actualCity)) // 地理位置信息
                    .gid(gid) // 短链接的唯一标识
                    .fullShortUrl(fullShortUrl) // 短链接的完整 URL
                    .build();
            linkAccessLogsMapper.insert(linkAccessLogsDO);

            // 增加短链接的统计数据（PV, UV, UIP）
            shortLinkMapper.incrementStats(gid, fullShortUrl, 1, statsRecord.getUvFirstFlag() ? 1 : 0, statsRecord.getUipFirstFlag() ? 1 : 0);

            // 创建并保存今日的短链接统计数据
            LinkStatsTodayDO linkStatsTodayDO = LinkStatsTodayDO.builder()
                    .todayPv(1) // 今日页面访问量 PV
                    .todayUv(statsRecord.getUvFirstFlag() ? 1 : 0) // 今日独立访客 UV
                    .todayUip(statsRecord.getUipFirstFlag() ? 1 : 0) // 今日唯一 IP UIP
                    .gid(gid) // 短链接的唯一标识
                    .fullShortUrl(fullShortUrl) // 短链接的完整 URL
                    .date(new Date()) // 当前日期
                    .build();
            linkStatsTodayMapper.shortLinkTodayState(linkStatsTodayDO);
        } catch (Throwable ex) {
            // 处理异常
            log.error("短链接访问量统计异常", ex);
        } finally {
            // 确保释放读锁
            rLock.unlock();
        }
    }
}
