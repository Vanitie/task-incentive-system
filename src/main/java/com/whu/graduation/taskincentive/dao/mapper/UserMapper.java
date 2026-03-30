package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.Date;
import java.util.List;
import java.util.Map;

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

    @Update("UPDATE `user` SET point_balance = #{pointBalance} WHERE id = #{userId}")
    int setUserPointBalance(@Param("userId") Long userId,
                            @Param("pointBalance") Integer pointBalance);

    /**
     * 统计用户总数
     */
    @Select("SELECT COUNT(1) FROM `user`")
    long countAllUsers();

    /**
     * 统计在指定截止时间（不含）之前的用户总数（按 create_time < end）
     */
    @Select("SELECT COUNT(1) FROM `user` WHERE create_time < #{end}")
    long countUsersBefore(@Param("end") Date end);

    /**
     * 按天统计新增用户数（基于 create_time），在 [start, end] 区间内按日期分组
     * 返回 List of map: { the_date => '2026-03-10', cnt => 123 }
     */
    @Select("SELECT DATE(create_time) as the_date, COUNT(DISTINCT id) as cnt FROM `user` WHERE create_time >= #{start} AND create_time < #{end} GROUP BY DATE(create_time) ORDER BY DATE(create_time) ASC")
    List<Map<String, Object>> countUsersGroupByDate(@Param("start") Date start, @Param("end") Date end);
}