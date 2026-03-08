package com.whu.graduation.taskincentive.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.whu.graduation.taskincentive.dao.entity.User;
import com.whu.graduation.taskincentive.dao.mapper.UserMapper;
import com.whu.graduation.taskincentive.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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

    private final UserMapper userMapper;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

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
    public Page<User> selectPage(Page<User> page) {
        return this.baseMapper.selectPage(page, null);
    }

    @Override
    public User selectByUsername(String username) {
        return this.baseMapper.selectByUsername(username);
    }

    @Override
    public boolean updateUserPoints(Long userId, Integer points) {
        return this.baseMapper.updateUserPoints(userId, points) > 0;
    }

    @Override
    public boolean register(User user, String rawPassword, String roles) {
        if (user == null || user.getUsername() == null) {
            return false;
        }
        User exists = userMapper.selectByUsername(user.getUsername());
        if (exists != null) {
            return false;
        }
        user.setId(IdWorker.getId());
        user.setPassword(passwordEncoder.encode(rawPassword));
        user.setRoles(roles);
        return super.save(user);
    }

    @Override
    public User authenticate(String username, String rawPassword) {
        User u = userMapper.selectByUsername(username);
        if (u == null) return null;
        if (passwordEncoder.matches(rawPassword, u.getPassword())) {
            // 返回不带密码的用户对象
            User safe = new User();
            safe.setId(u.getId());
            safe.setUsername(u.getUsername());
            safe.setRoles(u.getRoles());
            safe.setPointBalance(u.getPointBalance());
            safe.setCreateTime(u.getCreateTime());
            safe.setUpdateTime(u.getUpdateTime());
            return safe;
        }
        return null;
    }
}