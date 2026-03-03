package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    /**
     * 按用户名查询用户
     */
    @Select("SELECT * FROM `user` WHERE username = #{username}")
    User selectByUsername(@Param("username") String username);

    /**
     * 更新用户积分（加/减）
     */
    @Update("UPDATE `user` SET point_balance = point_balance + #{points} WHERE id = #{userId}")
    int updateUserPoints(@Param("userId") Long userId,
                         @Param("points") Integer points);
}