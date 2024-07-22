package com.project.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.date.Week;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.ArrayUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.project.shortlink.project.common.convention.exception.ClientException;
import com.project.shortlink.project.common.convention.exception.ServiceException;
import com.project.shortlink.project.common.enums.ValidDateEnum;
import com.project.shortlink.project.dao.entity.*;
import com.project.shortlink.project.dao.mapper.*;
import com.project.shortlink.project.dto.req.ShortLinkCreateReqDTO;
import com.project.shortlink.project.dto.req.ShortLinkPageReqDTO;
import com.project.shortlink.project.dto.req.ShortLinkUpdateReqDTO;
import com.project.shortlink.project.dto.resp.ShortLinkCreateRespDTO;
import com.project.shortlink.project.dto.resp.ShortLinkGroupCountQueryRespDTO;
import com.project.shortlink.project.dto.resp.ShortLinkPageRespDTO;
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
import static com.project.shortlink.project.common.constant.ShortLinkConstant.AMAP_REMOTE_URL;

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
    private final LinkAccessStatsMapper linkAccessStatsMapper;
    private final LinkLocaleStatsMapper linkLocaleStatsMapper;
    private final LinkOsStatsMapper linkOsStatsMapper;
    private final LinkBrowserStatsMapper linkBrowserStatsMapper;
    private final LinkAccessLogsMapper linkAccessLogsMapper;
    private final LinkDeviceStatsMapper linkDeviceStatsMapper;
    private final LinkNetworkStatsMapper linkNetworkStatsMapper;
    private final LinkStatsTodayMapper linkStatsTodayMapper;

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
                .createType(requestParam.getCreateType())
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
                    .createType(hasShortLinkDO.getCreateType())
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
                        .createType(hasShortLinkDO.getCreateType())
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
            //跳转
            linkAccessStats(fullShortUrl, null, request, response);
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
                linkAccessStats(fullShortUrl, null, request, response);
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
            linkAccessStats(fullShortUrl, gid, request, response);
            ((HttpServletResponse) response).sendRedirect(originUrl);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 监控短链接状态
     *
     * @param gid          短链接分组id
     * @param fullShortUrl 完整短链接
     * @param request      Http请求
     * @param response     Http响应
     */
    private void linkAccessStats(String fullShortUrl, String gid, ServletRequest request, ServletResponse response) {
        //是否是用户第一次访问该短链接
        AtomicBoolean uvFirstFlag = new AtomicBoolean();

        AtomicReference<String> uv = new AtomicReference<>();

        Cookie[] cookies = ((HttpServletRequest) request).getCookies();
        try {
            /**
             * 任务，生成UUID，加入Redis中
             */
            Runnable addResponseCookieTask = () -> {
                uv.set(UUID.fastUUID().toString());
                Cookie uvCookie = new Cookie("uv", uv.get());
                uvCookie.setMaxAge(60 * 60 * 24 * 30);
                uvCookie.setPath(StrUtil.sub(fullShortUrl, fullShortUrl.indexOf("/"), fullShortUrl.length()));
                ((HttpServletResponse) response).addCookie(uvCookie);
                uvFirstFlag.set(Boolean.TRUE);
                stringRedisTemplate.opsForSet().add("short-link:stats:uv:" + fullShortUrl, uv.get());
            };
            /**
             * 判断uvFirstFlag是否为true
             * 将用户的cookie写入Redis，如果Redis中没有，就是第一次访问
             */
            if (ArrayUtil.isNotEmpty(cookies)) {
                Arrays.stream(cookies)
                        .filter(each -> Objects.equals(each.getName(), "uv"))
                        .findFirst()
                        .map(Cookie::getValue)
                        .ifPresentOrElse(each -> {
                            uv.set(each);
                            Long uvAdded = stringRedisTemplate.opsForSet().add("short-link:stats:uv:" + fullShortUrl, each);
                            uvFirstFlag.set(uvAdded != null && uvAdded > 0L);
                        }, addResponseCookieTask);
            } else {
                addResponseCookieTask.run();
            }
            /**
             * 判断uipFirstFlag是否为true
             * 将用户的ip写入Redis，如果Redis中没有，就是第一次访问
             */
            String remoteAddr = LinkUtil.getActualIp(((HttpServletRequest) request));
            Long uipAdded = stringRedisTemplate.opsForSet().add("short-link:stats:uip:" + fullShortUrl, remoteAddr);
            boolean uipFirstFlag = uipAdded != null && uipAdded > 0L;
            /**
             * 如果传进来的gid为空，我们就去goto表查询
             */
            if (StrUtil.isBlank(gid)) {
                LambdaQueryWrapper<ShortLinkGotoDO> queryWrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                        .eq(ShortLinkGotoDO::getFullShortUrl, fullShortUrl);
                ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(queryWrapper);
                gid = shortLinkGotoDO.getGid();
            }
            /**
             *  访问小时数和访问星期数
             */
            int hour = DateUtil.hour(new Date(), true);
            Week weekday = DateUtil.dayOfWeekEnum(new Date());
            int weekValue = weekday.getIso8601Value();
            /**
             * 下面执行mybatis写的sql语句
             * 1. 如果数据库中没有记录就创建一条
             * 2. 如果有记录，就进行相应的修改
             */
            LinkAccessStatsDO linkAccessStatsDO = LinkAccessStatsDO.builder()
                    .gid(gid)
                    .fullShortUrl(fullShortUrl)
                    .date(new Date())
                    .weekday(weekValue)
                    .hour(hour)
                    .pv(1)
                    .uv(uvFirstFlag.get() ? 1 : 0)
                    .uip(uipFirstFlag ? 1 : 0)
                    .build();
            linkAccessStatsMapper.shortLinkStats(linkAccessStatsDO);
            /**
             * 监控地区状态信息
             */
            Map<String, Object> localeParamMap = new HashMap<>();
            localeParamMap.put("key", statsLocaleAmapKey);
            localeParamMap.put("ip", remoteAddr);
            String localeResultStr = HttpUtil.get(AMAP_REMOTE_URL, localeParamMap);
            JSONObject localeResultObj = JSON.parseObject(localeResultStr);
            String infoCode = localeResultObj.getString("infocode");
            if (StrUtil.isNotBlank(infoCode) && StrUtil.equals(infoCode, "10000")) {
                String province = localeResultObj.getString("province");
                boolean unknownFlag = StrUtil.equals(province, "[]");
                LinkLocaleStatsDO linkLocaleStatsDO = LinkLocaleStatsDO.builder()
                        .province(unknownFlag ? "未知" : province)
                        .city(unknownFlag ? "未知" : localeResultObj.getString("city"))
                        .adcode(unknownFlag ? "未知" : localeResultObj.getString("adcode"))
                        .cnt(1)
                        .fullShortUrl(fullShortUrl)
                        .country("中国")
                        .gid(gid)
                        .date(new Date())
                        .build();
                linkLocaleStatsMapper.shortLinkLocaleState(linkLocaleStatsDO);
                /**
                 * 监控操作系统情况
                 */
                String os = LinkUtil.getOs(((HttpServletRequest) request));
                LinkOsStatsDO linkOsStatsDO = LinkOsStatsDO.builder()
                        .os(os)
                        .cnt(1)
                        .gid(gid)
                        .fullShortUrl(fullShortUrl)
                        .date(new Date())
                        .build();
                linkOsStatsMapper.shortLinkOsState(linkOsStatsDO);
                /**
                 * 监控浏览器情况
                 */
                String browser = LinkUtil.getBrowser(((HttpServletRequest) request));
                LinkBrowserStatsDO linkBrowserStatsDO = LinkBrowserStatsDO.builder()
                        .browser(browser)
                        .cnt(1)
                        .gid(gid)
                        .fullShortUrl(fullShortUrl)
                        .date(new Date())
                        .build();
                linkBrowserStatsMapper.shortLinkBrowserState(linkBrowserStatsDO);

                /**
                 * 监控日志统计，用来统计高频ip
                 */
                LinkAccessLogsDO linkAccessLogsDO = LinkAccessLogsDO.builder()
                        .user(uv.get())
                        .ip(remoteAddr)
                        .browser(browser)
                        .os(os)
                        .gid(gid)
                        .fullShortUrl(fullShortUrl)
                        .build();
                linkAccessLogsMapper.insert(linkAccessLogsDO);
                /**
                 * 访问设备监控
                 */
                LinkDeviceStatsDO linkDeviceStatsDO = LinkDeviceStatsDO.builder()
                        .device(LinkUtil.getDevice(((HttpServletRequest) request)))
                        .cnt(1)
                        .gid(gid)
                        .fullShortUrl(fullShortUrl)
                        .date(new Date())
                        .build();
                linkDeviceStatsMapper.shortLinkDeviceState(linkDeviceStatsDO);
                /**
                 * 访问网络监控
                 */
                LinkNetworkStatsDO linkNetworkStatsDO = LinkNetworkStatsDO.builder()
                        .network(LinkUtil.getNetwork(((HttpServletRequest) request)))
                        .cnt(1)
                        .gid(gid)
                        .fullShortUrl(fullShortUrl)
                        .date(new Date())
                        .build();
                linkNetworkStatsMapper.shortLinkNetworkState(linkNetworkStatsDO);
                /**
                 * 统计完数据，将总访问量、uv/uip等进行自增
                 */
                baseMapper.incrementStats(gid, fullShortUrl, 1, uvFirstFlag.get() ? 1 : 0, uipFirstFlag ? 1 : 0);
                /**
                 * 记录今日状态
                 */
                LinkStatsTodayDO linkStatsTodayDO = LinkStatsTodayDO.builder()
                        .todayPv(1)
                        .todayUv(uvFirstFlag.get() ? 1 : 0)
                        .todayUip(uipFirstFlag ? 1 : 0)
                        .gid(gid)
                        .fullShortUrl(fullShortUrl)
                        .date(new Date())
                        .build();
                linkStatsTodayMapper.shortLinkTodayState(linkStatsTodayDO);
            }
        } catch (Throwable ex) {
            log.error("短链接访问量统计异常", ex);
        }
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
