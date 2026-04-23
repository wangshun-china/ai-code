package com.ws.codecraftuser.service.impl;

import com.ws.codecraft.innerservice.InnerUserService;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.vo.UserVO;
import com.ws.codecraftuser.service.UserService;
import jakarta.annotation.Resource;
import org.apache.dubbo.config.annotation.DubboService;

import java.io.Serializable;
import java.util.Collection;
import java.util.List;

/**
 * 内部服务实现类
 */
@DubboService
public class InnerUserServiceImpl implements InnerUserService {

    @Resource
    private UserService userService;

    @Override
    public List<User> listByIds(Collection<? extends Serializable> ids) {
        return userService.listByIds(ids);
    }

    @Override
    public User getById(Serializable id) {
        return userService.getById(id);
    }

    @Override
    public User getAuthUserById(Serializable id) {
        if (id == null) {
            return null;
        }
        return userService.getAuthUserById(Long.valueOf(String.valueOf(id)));
    }

    @Override
    public UserVO getUserVO(User user) {
        return userService.getUserVO(user);
    }
}
