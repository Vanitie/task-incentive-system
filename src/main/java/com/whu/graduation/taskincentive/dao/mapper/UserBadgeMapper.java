package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.UserBadge;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.springframework.data.repository.query.Param;

@Mapper
public interface UserBadgeMapper extends BaseMapper<UserBadge> {
    @Select("""
        SELECT * FROM user_badge 
        WHERE user_id = #{userId} 
        AND badge_id = #{badgeId}
    """)
    UserBadge selectUserBadge(
            @Param("userId") Long userId,
            @Param("badgeId") Long badgeId
    );
}