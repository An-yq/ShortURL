package com.project.shortlink.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.lang.UUID;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.project.shortlink.admin.common.convention.exception.ClientException;
import com.project.shortlink.admin.common.convention.result.Result;
import com.project.shortlink.admin.common.convention.result.Results;
import com.project.shortlink.admin.dao.entity.UserDO;
import com.project.shortlink.admin.dao.mapper.UserMapper;
import com.project.shortlink.admin.dto.req.UserLoginReqDTO;
import com.project.shortlink.admin.dto.req.UserRegisterReqDTO;
import com.project.shortlink.admin.dto.req.UserUpdateReqDTO;
import com.project.shortlink.admin.dto.resp.UserLoginRespDTO;
import com.project.shortlink.admin.dto.resp.UserRespDTO;
import com.project.shortlink.admin.service.UserService;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static com.project.shortlink.admin.common.constant.RedisCacheConstant.LOCK_USER_REGISTER_KEY_PREFIX;
import static com.project.shortlink.admin.common.enums.UserErrorCode.*;

@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, UserDO> implements UserService {
    private final RBloomFilter<String> userRegisterCachePenetrationBloomFilter;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    @Override
    public Result<UserRespDTO> GetUserByUsername(String username) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, username);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if(userDO == null){
            throw new ClientException(USER_NULL);
        }
        UserRespDTO userRespDTO = new UserRespDTO();
        BeanUtils.copyProperties(userDO,userRespDTO);
        return Results.success(userRespDTO);
    }

    @Override
    public Boolean hasUsername(String username) {
        return !userRegisterCachePenetrationBloomFilter.contains(username);
    }

    @Override
    public void register(UserRegisterReqDTO requestParam) {
        if(!hasUsername(requestParam.getUsername())){
            throw new ClientException(USER_NAME_EXIST);
        }
        RLock lock = redissonClient.getLock(LOCK_USER_REGISTER_KEY_PREFIX + requestParam.getUsername());
        try {
            if (!lock.tryLock()) {
                throw new ClientException(USER_EXIST);
            }
            userRegisterCachePenetrationBloomFilter.add(requestParam.getUsername());
            UserDO userDO = BeanUtil.toBean(requestParam, UserDO.class);
            int insert = baseMapper.insert(userDO);
            if(insert < 1){
                throw new ClientException(USER_SAVE_FAIL);
            }
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void update(UserUpdateReqDTO requestParam) {
        LambdaUpdateWrapper<UserDO> updateWrapper = Wrappers.lambdaUpdate(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername());
        baseMapper.update(BeanUtil.toBean(requestParam,UserDO.class),updateWrapper);
    }

    @Override
    public UserLoginRespDTO login(UserLoginReqDTO requestParam) {
        LambdaQueryWrapper<UserDO> queryWrapper = Wrappers.lambdaQuery(UserDO.class)
                .eq(UserDO::getUsername, requestParam.getUsername())
                .eq(UserDO::getPassword, requestParam.getPassword())
                .eq(UserDO::getDelFlag,0);
        UserDO userDO = baseMapper.selectOne(queryWrapper);
        if(userDO == null){
            throw new ClientException(USER_NULL);
        }
        Boolean hasKey = stringRedisTemplate.hasKey("login_" + requestParam.getUsername());
        if(hasKey != null && hasKey){
            throw new ClientException("用户已登录");
        }
        //将token和用户信息存入缓存
        String uuid = UUID.randomUUID().toString(true);
        stringRedisTemplate.opsForHash().put("login_" + userDO.getUsername(),uuid, JSONUtil.toJsonStr(userDO));
        stringRedisTemplate.expire("login_" + userDO.getUsername(),30L, TimeUnit.DAYS);

        return new UserLoginRespDTO(uuid);
    }

    @Override
    public Boolean checkLogin(String token,String username) {
        return stringRedisTemplate.opsForHash().hasKey("login_" + username,token);
    }

    @Override
    public void logout(String token, String username) {
        //删除redis缓存
        if(!stringRedisTemplate.opsForHash().hasKey("login_" + username,token)){
            throw new ClientException("用户未登录");
        }
        stringRedisTemplate.delete("login_" + username);
    }
}