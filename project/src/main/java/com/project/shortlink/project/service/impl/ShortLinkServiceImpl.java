package com.project.shortlink.project.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.project.shortlink.project.common.convention.exception.ServiceException;
import com.project.shortlink.project.dao.entity.ShortLinkDO;
import com.project.shortlink.project.dao.entity.ShortLinkGotoDO;
import com.project.shortlink.project.dao.mapper.ShortLinkGotoMapper;
import com.project.shortlink.project.dao.mapper.ShortLinkMapper;
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
import org.redisson.api.RedissonClient;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
                .build();
        //insert的时候加上try-catch，加上一层保险
        try {
            baseMapper.insert(shortLinkDO);
            //将短链接保存到缓存中，缓存预热
            stringRedisTemplate.opsForValue().set(
                    String.format(GOTO_SHORT_LINK_KEY,fullShortUrl),
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
        LambdaQueryWrapper<ShortLinkDO> wrapper = Wrappers.lambdaQuery(ShortLinkDO.class)
                .eq(ShortLinkDO::getGid, requestParam.getGid())
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getEnableStatus, 0)
                .orderByDesc(ShortLinkDO::getCreateTime);
        //分页查询
        IPage<ShortLinkDO> resultPage = baseMapper.selectPage(requestParam, wrapper);
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
    public Void updateShortLink(ShortLinkUpdateReqDTO requestParam) {
        ShortLinkDO shortLinkDO = BeanUtil.toBean(requestParam, ShortLinkDO.class);
        LambdaUpdateWrapper<ShortLinkDO> wrapper = Wrappers.lambdaUpdate(ShortLinkDO.class)
                .eq(ShortLinkDO::getFullShortUrl, requestParam.getFullShortUrl())
                .eq(ShortLinkDO::getDelFlag, 0)
                .eq(ShortLinkDO::getEnableStatus, 0);

        //TODO 完成短链接修改功能
        return null;
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
        if(StrUtil.isNotBlank(originUrl)){
            //跳转
            ((HttpServletResponse)response).sendRedirect(originUrl);
            return;
        }
        //3. 查询布隆过滤器中是否存在
        boolean contains = shortUriCreateCachePenetrationBloomFilter.contains(fullShortUrl);
        if(!contains){
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }
        //4. Redis判断是否为空的字段
        String isNull = stringRedisTemplate.opsForValue().get(String.format(GOTO_SHORT_LINK_IS_NULL_KEY,fullShortUrl));
        if(StrUtil.isNotBlank(isNull)){
            ((HttpServletResponse) response).sendRedirect("/page/notfound");
            return;
        }
        //5. 加分布式锁，开始数据库查询
        RLock lock = redissonClient.getLock(String.format(LOCK_GOTO_SHORT_LINK_KEY, fullShortUrl));
        try {
            lock.lock();
            //6. 在goto表里面查询短链接所在分组
            LambdaQueryWrapper<ShortLinkGotoDO> wrapper = Wrappers.lambdaQuery(ShortLinkGotoDO.class)
                    .eq(ShortLinkGotoDO::getFullShortUrl, serverName + "/" + shortUri);
            ShortLinkGotoDO shortLinkGotoDO = shortLinkGotoMapper.selectOne(wrapper);
            if (shortLinkGotoDO == null) {
                //7. 数据库中不存在，将连接标记为无效
                stringRedisTemplate.opsForValue().set(
                        String.format(GOTO_SHORT_LINK_IS_NULL_KEY,fullShortUrl),
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
                    .eq(ShortLinkDO::getFullShortUrl,fullShortUrl);
            ShortLinkDO shortLinkDO = baseMapper.selectOne(queryWrapper);
            //判空,重定向----如果这里为空，说明他在回收站，不能用
            if (shortLinkDO == null || shortLinkDO.getValidDate().before(new Date())) {
                ((HttpServletResponse) response).sendRedirect("/page/notfound");
                //不要忘记判空
                return;
            }
            originUrl = shortLinkDO.getOriginUrl();
            stringRedisTemplate.opsForValue().set(
                    String.format(GOTO_SHORT_LINK_KEY,fullShortUrl),
                    originUrl,
                    LinkUtil.getValidTime(shortLinkDO.getValidDate()),
                    TimeUnit.MILLISECONDS);
            ((HttpServletResponse)response).sendRedirect(originUrl);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 获取网站图标
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
