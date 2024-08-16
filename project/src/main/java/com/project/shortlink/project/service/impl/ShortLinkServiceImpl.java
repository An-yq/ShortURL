package com.project.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.project.shortlink.project.common.convention.exception.ClientException;
import com.project.shortlink.project.common.convention.exception.ServiceException;
import com.project.shortlink.project.common.enums.ValidDateEnum;
import com.project.shortlink.project.dao.entity.ShortLinkDO;
import com.project.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.project.shortlink.project.dao.mapper.*;
import com.project.shortlink.project.dto.biz.ShortLinkStatsRecordDTO;
import com.project.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.project.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.project.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.project.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.project.shortlink.project.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.project.shortlink.project.dto.resp.ShortLinkPageRespDTO;
import com.project.shortlink.project.mq.Producer.ShortLinkStatsProducer;
import com.project.shortlink.project.service.ShortLinkService;
import com.project.shortlink.project.toolkit.HashUtil;
import com.project.shortlink.project.toolkit.LinkUtil;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.curator.shaded.com.google.common.collect.Lists;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.project.shortlink.project.common.constant.RedisConstant.*;

/**
 * ShortLink管理模块Service实现层
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ShortLinkServiceImpl extends ServiceImpl<ShortLinkMapper, ShortLinkDO> implements ShortLinkService {
    private final RBloomFilter<String> shortUriCreateCachePenetrationBloomFilter;
    private final ShortLinkGotoMapper shortLinkGotoMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedissonClient redissonClient;
    private final ShortLinkStatsProducer shortLinkStatsProducer;

    @Value("${short-link.stats.locale.amap-key}")
    private String statsLocaleAmapKey;

    @Override
    public ShortLinkCreateRespDTO createShortLink(ShortLinkCreateReqDTO requestParam) {
        //生成短链接后缀
        String suffix = generateSuffix(requestParam);
        String fullShortUrl = requestParam.getDomain() + "/" + suffix;
        //1. 将shortLink添加到路由表中
        ShortLinkGotoDO shortLinkGotoDO = ShortLinkGotoDO.builder()
                .gid(requestParam.getGid())
                .fullShortUrl(fullShortUrl)
                .build();
        shortLinkGotoMapper.insert(shortLinkGotoDO);
        //2. 将短链接添加到t_link表中
        ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                .enableStatus(0)
                .createdType(requestParam.getCreateType())
                .domain(requestParam.getDomain())
                .fullShortUrl(fullShortUrl)
                .shortUri(suffix)
                .originUrl(requestParam.getOriginUrl())
                .clickNum(0)
                .gid(requestParam.getGid())
                .validDateType(requestParam.getValidDateType())
                .validDate(requestParam.getValidDate())
                .describe(requestParam.getDescribe())
                .favicon(getFavicon(requestParam.getOriginUrl()))
                .totalPv(0)
                .totalUv(0)
                .totalUip(0)
                .build();
        //insert的时候加上try-catch，加上一层保险
        try {
            baseMapper.insert(shortLinkDO);
            //将短链接保存到缓存中，缓存预热
            stringRedisTemplate.opsForValue().set(
                    String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                    requestParam.getOriginUrl(),
                    LinkUtil.getValidTime(requestParam.getValidDate()),
                    TimeUnit.MILLISECONDS);
        } catch (DuplicateKeyException e) {
            LambdaQueryWrapper<ShortLinkDO> wrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getFullShortUrl, shortLinkDO.getFullShortUrl());
            ShortLinkDO hasShortLinkDO = baseMapper.selectOne(wrapper);
            if (hasShortLinkDO != null) {
                log.warn("短链接 {} 重复入库", shortLinkDO.getFullShortUrl());
                throw new ServiceException("短链接生成重复");
            }
        }
        //将短链接添加到布隆过滤器中
        shortUriCreateCachePenetrationBloomFilter.add(fullShortUrl);
        return ShortLinkCreateRespDTO.builder()
                .gid(shortLinkDO.getGid())
                .fullShortUrl("http://" + shortLinkDO.getFullShortUrl())
                .originUrl(shortLinkDO.getOriginUrl()).build();
    }

    @Override
    public IPage<ShortLinkPageRespDTO> pageShortLink(ShortLinkPageReqDTO requestParam) {
        IPage<ShortLinkDO> resultPage = baseMapper.pageLink(requestParam);
        IPage<ShortLinkPageRespDTO> result = resultPage.convert(each -> BeanUtil.toBean(each, ShortLinkPageRespDTO.class));
        return result;
    }

    @Override
    public List<ShortLinkGroupCountQueryRespDTO> listGroupShortLinkCount(List<String> requestParam) {
        //查询gid下的短链接数量
        //select gid,count(*) from t_link where enable_status = 0 and gid in (requestParam的gid集合) group by gid;
        QueryWrapper<ShortLinkDO> wrapper = Wrappers.query(new ShortLinkDO())
                .select("gid", "count(*) as shortLinkCount")
                .eq("enable_status", 0)
                .in("gid", Lists.newArrayList(requestParam))
                .groupBy("gid");
        List<Map<String, Object>> shortLinkDOList = baseMapper.selectMaps(wrapper);
        return BeanUtil.copyToList(shortLinkDOList, ShortLinkGroupCountQueryRespDTO.class);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        //修改短链接
        //1.根据请求参数在数据库中查询短链接
        LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getOriginGid())
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getEnableStatus, 0);
        ShortLinkDO hasShortLinkDO = baseMapper.selectOne(queryWrapper);
        if(hasShortLinkDO == null){
            throw new ClientException("短链接记录不存在");
        }
        //2. 如果查询出来的短链接的分组标识和requestParam中要修改成的gid相同，就直接更新数据库即可（不用加读写锁）
        if(Objects.equals(hasShortLinkDO.getGid(),requestParam.getGid())){
            LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                    .eq(ShortLinkDO::getGid, requestParam.getGid())
                    .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0);
            ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                    .domain(hasShortLinkDO.getDomain())
                    .favicon(hasShortLinkDO.getFavicon())
                    .createdType(hasShortLinkDO.getCreatedType())
                    .fullShortUrl(hasShortLinkDO.getFullShortUrl())
                    .gid(requestParam.getGid())
                    .originUrl(requestParam.getOriginUrl())
                    .validDate(requestParam.getValidDate())
                    .validDateType(requestParam.getValidDateType())
                    .describe(requestParam.getDescribe())
                    .build();
            baseMapper.update(shortLinkDO,updateWrapper);
        }else {
            //3. 否则，就需要加读写锁，进行并发控制，保证写操作的安全
            RReadWriteLock readWriteLock = redissonClient.getReadWriteLock(String.format(LOCK_GID_UPDATE_KEY, requestParam.getFullShortUrl()));
            RLock rLock = readWriteLock.writeLock();
            //获取锁这里，加上try-finally结构，finally的时候释放锁
            rLock.lock();
            try {
                //开始进行写操作
                //(1) 将旧表中的删除
                LambdaUpdateWrapper<ShortLinkDO> updateWrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                        .eq(ShortLinkDO::getGid, requestParam.getOriginGid())
                        .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                        .eq(ShortLinkDO::getDelFlag, 0)
                        .eq(ShortLinkDO::getEnableStatus, 0);
                ShortLinkDO delShortLinkDO = new ShortLinkDO();
                delShortLinkDO.setDelFlag(1);
                baseMapper.update(delShortLinkDO, updateWrapper);
                //(2) 创建新的记录插入新的表中 --- 这里先不处理todayPv、Uv、Uip，后面异步处理，因为涉及到今日数据表
                ShortLinkDO shortLinkDO = ShortLinkDO.builder()
                        .domain(hasShortLinkDO.getDomain())
                        .favicon(hasShortLinkDO.getFavicon())
                        .createdType(hasShortLinkDO.getCreatedType())
                        .fullShortUrl(hasShortLinkDO.getFullShortUrl())
                        .gid(requestParam.getGid())
                        .originUrl(requestParam.getOriginUrl())
                        .shortUri(hasShortLinkDO.getShortUri())
                        .describe(requestParam.getDescribe())
                        .validDate(requestParam.getValidDate())
                        .validDateType(requestParam.getValidDateType())
                        .clickNum(hasShortLinkDO.getClickNum())
                        .enableStatus(hasShortLinkDO.getEnableStatus())
                        .totalPv(hasShortLinkDO.getTotalPv())
                        .totalUip(hasShortLinkDO.getTotalUip())
                        .totalUv(hasShortLinkDO.getTotalUv())
                        .build();
                baseMapper.insert(shortLinkDO);
                //(3) 更新路由表
                LambdaQueryWrapper<ShortLinkGotoDO> queryGotoWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                        .eq(ShortLinkGotoDO::getGid, requestParam.getOriginGid())
                        .eq(ShortLinkGotoDO::getFullShortUrl, requestParam.getFullShortUrl());
                ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryGotoWrapper);
                shortLinkGotoMapper.delete(queryGotoWrapper);
                shortLinkGotoDO.setGid(requestParam.getGid());
                shortLinkGotoMapper.insert(shortLinkGotoDO);

            }finally {
                rLock.unlock();
            }
            //4. 更新数据库，删除缓存，保证数据库和缓存数据一致性，两个缓存，一个是短链接记录缓存，一个是短链接是否为空缓存
            if(!Objects.equals(hasShortLinkDO.getValidDate(),requestParam.getValidDate())||
            !Objects.equals(hasShortLinkDO.getValidDateType(),requestParam.getValidDateType())||
            !Objects.equals(hasShortLinkDO.getOriginUrl(),requestParam.getOriginUrl())){
                stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_KEY,requestParam.getFullShortUrl()));
                Date currentDate = new Date();
                //先判断原始链接是否过期，过期的话，redis中会存一个短链接为空的标识
                if(hasShortLinkDO.getValidDate() != null && hasShortLinkDO.getValidDate().before(currentDate)){
                    //再判断requestParam中看是不是不过期了，看是否要删除这个参数
                    if(Objects.equals(requestParam.getValidDateType(), ValidDateEnum.PERMANENT.getType()) ||
                            requestParam.getValidDate().after(currentDate)){
                        stringRedisTemplate.delete(String.format(GOTO_SHORT_LINK_IS_NULL_KEY,requestParam.getFullShortUrl()));
                    }
                }
            }

        }
    }

    @SneakyThrows
    @Override
    public void restoreUrl(String shortUri, ServletRequest request, ServletResponse response) {
        /**
         * 根据短链接查询对应的长链接
         * 参数shortUri是短链接的后缀
         * 1. t_link表的分片规则是gid，但是我们在进行跳转的时候传进来的是full_short_url，会造成读扩散问题
         * 2. 我们需要创建一个路由表 t_link_goto，根据这个表路由到对应的gid
         * 3. 通过gid + 后缀名找到对应的原始连接
         */
        //根据url找到gid
        //1. 根据request找到短链接的域名
        String serverName = request.getServerName();
        String fullShortUrl = serverName + "/" + shortUri;
        //2. 查询Redis看是否能查到原始链接
        String originUrl = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
        if (StrUtil.isNotBlank(originUrl)) {
            //保存监控信息，跳转
            ShortLinkStatsRecordDTO statsRecord = buildLinkStatsRecordAndSetUser(fullShortUrl, request, response);
            linkAccessStats(fullShortUrl, null, statsRecord);
            ((HttpServletResponse) response).sendRedirect(originUrl);
            return;
        }
        //3. 查询布隆过滤器中是否存在
        boolean contains = shortUriCreateCachePenetrationBloomFilter.contains(fullShortUrl);
        if (!contains) {
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }
        //4. Redis判断是否为空的字段
        String isNull = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_IS_NULL_KEY, fullShortUrl));
        if (StrUtil.isNotBlank(isNull)) {
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }
        //5. 加分布式锁，开始数据库查询
        RLock lock = redissonClient.getLock(String.format(LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        lock.lock();
        try {
            originUrl = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_KEY, fullShortUrl));
            if (StrUtil.isNotBlank(originUrl)) {
                ShortLinkStatsRecordDTO statsRecord = buildLinkStatsRecordAndSetUser(fullShortUrl, request, response);
                linkAccessStats(fullShortUrl, null, statsRecord);
                ((HttpServletResponse) response).sendRedirect(originUrl);
                return;
            }
            //6. 在goto表里面查询短链接所在分组
            LambdaQueryWrapper<ShortLinkGotoDO> wrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, serverName + "/" + shortUri);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(wrapper);
            if (shortLinkGotoDO == null) {
                //7. 数据库中不存在，将连接标记为无效
                stringRedisTemplate.opsForValue().set(
                        String.format(GOTO_SHORT_LINK_IS_NULL_KEY, fullShortUrl),
                        "-",
                        30,
                        TimeUnit.MINUTES);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                //不要忘记判空
                return;
            }
            //3. shortLinkGotoDO对象获取gid
            String gid = shortLinkGotoDO.getGid();
            //找原始链接
            LambdaQueryWrapper<ShortLinkDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                    .eq(ShortLinkDO::getDelFlag, 0)
                    .eq(ShortLinkDO::getEnableStatus, 0)
                    .eq(ShortLinkDO::getGid, gid)
                    .eq(ShortLinkDO::getFullShortUrl, fullShortUrl);
            ShortLinkDO shortLinkDO = baseMapper.selectOne(queryWrapper);
            //判空,重定向----如果这里为空，说明他在回收站，不能用
            if (shortLinkDO == null || shortLinkDO.getValidDate().before(new Date())) {
                stringRedisTemplate.opsForValue().set(String.format(GOTO_SHORT_LINK_IS_NULL_KEY, fullShortUrl), "-", 30, TimeUnit.MINUTES);
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                //不要忘记判空
                return;
            }
            originUrl = shortLinkDO.getOriginUrl();
            stringRedisTemplate.opsForValue().set(
                    String.format(GOTO_SHORT_LINK_KEY, fullShortUrl),
                    originUrl,
                    LinkUtil.getValidTime(shortLinkDO.getValidDate()),
                    TimeUnit.MILLISECONDS);
            //重定向之前，将监控信息保存
            ShortLinkStatsRecordDTO statsRecord = buildLinkStatsRecordAndSetUser(fullShortUrl, request, response);
            linkAccessStats(fullShortUrl,shortLinkDO.getGid(),statsRecord);
            ((HttpServletResponse) response).sendRedirect(originUrl);
        } finally {
            lock.unlock();
        }
    }


    /**
     * 跳转短链接的时候，将短链接监控信息保存
     * @param shortLinkStatsRecordDTO 业务之间传输的监控信息保存类，这个类里面有详细的信息
     */
    @Override
    public void linkAccessStats(String fullShortUrl, String gid, ShortLinkStatsRecordDTO shortLinkStatsRecordDTO) {
        //将需要的数据放入hashMap，然后通过producer发送消息到mq
        HashMap<String, String> producerMap = new HashMap<>();
        producerMap.put("fullShortUrl", fullShortUrl);
        producerMap.put("gid", gid);
        producerMap.put("statsRecord", JSONUtil.toJsonStr(producerMap));
        shortLinkStatsProducer.send(producerMap);
    }

    /**
     * 短链接监控信息保存之前的构建 -- 构建uvFirstFlag和uipFirstFlag，便于后面进行监控信息报讯
     * @return
     */
    private ShortLinkStatsRecordDTO buildLinkStatsRecordAndSetUser(String fullShortUrl , ServletRequest request , ServletResponse response){
        //用户是否第一次访问短链接
        AtomicBoolean uvFirstFlag = new AtomicBoolean();
        //访问的uv是什么
        /**
         * AtomicReference 是线程安全的，保证了对 uv 的读写操作是原子的。
         * 这意味着，即使多个线程同时访问 uv，每个线程都能正确地读取和更新 uv，避免了数据竞争和不一致。
         */
        AtomicReference<String> uv = new AtomicReference<>();
        //在request中得到的cookie
        Cookie[] cookies = ((HttpServletRequest) request).getCookies();

        //新建一个runnable用来处理新建uv保存到cookie
        //注意 lambda表达式，重写run方法
        Runnable addResponseCookieTask = () -> {
            //将uv写到Reference中，保证线程安全，新建一个UUID当做uv的标记
            uv.set(UUID.fastUUID().toString(true));
            //新建一个uv的cookie
            Cookie cookie = new Cookie("uv",uv.get());
            cookie.setMaxAge(60 * 60 * 24 * 30);//设置cookie30天
            cookie.setPath(StrUtil.sub(fullShortUrl, fullShortUrl.indexOf("/"), fullShortUrl.length()));
            //将uv添加到response中
            ((HttpServletResponse)response).addCookie(cookie);
            //将uvFirstFlag标记为TRUE
            uvFirstFlag.set(Boolean.TRUE);
            //将uv添加到缓存中
            stringRedisTemplate.opsForSet().add("short-link:stats:uv:" + fullShortUrl, uv.get());
        };

        //判断cookie中的uv是否存在
        if(ArrayUtil.isNotEmpty(cookies)){
            //使用stream新特性
            Arrays.stream(cookies)
                    .filter(each -> Objects.equals(each.getName(),"uv"))
                    .findFirst()
                    //将Cookie的值转化成了String，转化成了Cookie的value，也就是实际的uv
                    .map(Cookie::getValue)
                    .ifPresentOrElse(each -> {
                        uv.set(each);
                        stringRedisTemplate.opsForSet().add("short-link:stats:uv:" + fullShortUrl, each);
                    },addResponseCookieTask);
        }else {
            addResponseCookieTask.run();
        }

        //将一些简单的基础信息，添加到DTO里面
        String remoteAddr = LinkUtil.getActualIp(((HttpServletRequest) request));
        String os = LinkUtil.getOs(((HttpServletRequest) request));
        String browser = LinkUtil.getBrowser(((HttpServletRequest) request));
        String device = LinkUtil.getDevice(((HttpServletRequest) request));
        String network = LinkUtil.getNetwork(((HttpServletRequest) request));
        //uip保存在了redis中，看一下能不能添加到缓存，来判断这个uipFirst
        Long add = stringRedisTemplate.opsForSet().add(SHORT_LINK_STATS_UIP_KEY + fullShortUrl, remoteAddr);
        boolean uipFirstFlag = add != null && add > 0L;
        //构造DTO
        return ShortLinkStatsRecordDTO.builder()
                .os(os)
                .network(network)
                .browser(browser)
                .device(device)
                .uvFirstFlag(uvFirstFlag.get())
                .uipFirstFlag(uipFirstFlag)
                .remoteAddr(remoteAddr)
                .fullShortUrl(fullShortUrl)
                .build();
    }


    /**
     * 获取网站图标
     *
     * @param url 网站地址
     * @return 图标
     */
    @SneakyThrows
    private String getFavicon(String url) {
        URL targetUrl = new URL(url);
        HttpURLConnection connection = (HttpURLConnection) targetUrl.openConnection();
        connection.setRequestMethod("GET");
        connection.connect();
        int responseCode = connection.getResponseCode();
        if (HttpURLConnection.HTTP_OK == responseCode) {
            Document document = Jsoup.connect(url).get();
            Element faviconLink = document.select("link[rel~=(?i)^(shortcut )?icon]").first();
            if (faviconLink != null) {
                return faviconLink.attr("abs:href");
            }
        }
        return null;
    }

    /**
     * 生成短链接后缀
     *
     * @param requestParam 短链接创建实体对象
     * @return 短链接后缀
     */
    private String generateSuffix(ShortLinkCreateReqDTO requestParam) {
        String shortUri = null;
        //加上当前的毫秒数，减少hash冲突
        int customGenerateCount = 0;
        //while循环生成短链接
        while (true) {
            if (customGenerateCount > 10) {
                throw new ServiceException("短链接生成频繁，请稍后再试");
            }
            String originUrl = requestParam.getOriginUrl();
            //加上当前的毫秒数，减少hash冲突
            originUrl += System.currentTimeMillis();
            shortUri = HashUtil.hashToBase62(originUrl);
            if (!shortUriCreateCachePenetrationBloomFilter.contains(shortUri)) {
                break;
            }
            customGenerateCount++;
        }
        return shortUri;
    }

}
