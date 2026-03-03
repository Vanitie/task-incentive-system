package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 用户服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
        implements UserService {

    @Override
    public boolean save(User user) {
        user.setId(IdWorker.getId());
        return super.save(user);
    }

    @Override
    public boolean update(User user) {
        return super.updateById(user);
    }

    @Override
    public boolean deleteById(Long id) {
        return super.removeById(id);
    }

    @Override
    public User getById(Long id) {
        return super.getById(id);
    }

    @Override
    public List<User> listAll() {
        return super.list();
    }

    @Override
    public User selectByUsername(String username) {
        return this.baseMapper.selectByUsername(username);
    }

    @Override
    public boolean updateUserPoints(Long userId, Integer points) {
        return this.baseMapper.updateUserPoints(userId, points) > 0;
    }
}