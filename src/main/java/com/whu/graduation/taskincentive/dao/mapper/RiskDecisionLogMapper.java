package com.whu.graduation.taskincentive.dao.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.whu.graduation.taskincentive.dao.entity.RiskDecisionLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Date;
import java.util.List;
import java.util.Map;

@Mapper
public interface RiskDecisionLogMapper extends BaseMapper<RiskDecisionLog> {
    @Select("SELECT decision, COUNT(*) AS cnt FROM risk_decision_log WHERE created_at >= #{start} AND created_at < #{end} GROUP BY decision")
    List<Map<String, Object>> countByDecision(@Param("start") Date start, @Param("end") Date end);

    @Select("SELECT DATE(created_at) AS the_date, decision, COUNT(*) AS cnt FROM risk_decision_log WHERE created_at >= #{start} AND created_at < #{end} GROUP BY DATE(created_at), decision ORDER BY DATE(created_at) ASC")
    List<Map<String, Object>> countDailyByDecision(@Param("start") Date start, @Param("end") Date end);

    @Select("SELECT latency_ms FROM risk_decision_log WHERE created_at >= #{start} AND created_at < #{end} AND latency_ms IS NOT NULL")
    List<Long> selectLatencies(@Param("start") Date start, @Param("end") Date end);
}
