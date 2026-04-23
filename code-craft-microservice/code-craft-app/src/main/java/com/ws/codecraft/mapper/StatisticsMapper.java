package com.ws.codecraft.mapper;

import com.mybatisflex.core.BaseMapper;
import com.ws.codecraft.model.entity.AiUsageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * AI 统计聚合查询 Mapper。
 */
@Mapper
public interface StatisticsMapper extends BaseMapper<AiUsageRecord> {

    @Select("""
            SELECT
                COALESCE(SUM(total_tokens), 0) AS total_tokens,
                COALESCE(SUM(input_tokens), 0) AS input_tokens,
                COALESCE(SUM(output_tokens), 0) AS output_tokens,
                COUNT(*) AS total_requests,
                SUM(CASE WHEN request_status = 'error' THEN 1 ELSE 0 END) AS total_errors,
                COALESCE(AVG(response_time_ms), 0) AS avg_response_time
            FROM ai_usage_record
            WHERE request_time >= #{start} AND request_time < #{endExclusive}
            """)
    Map<String, Object> selectTotalMetrics(@Param("start") LocalDateTime start,
                                           @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT
                COALESCE(NULLIF(model_name, ''), 'unknown') AS model_name,
                SUM(total_tokens) AS total_tokens,
                COUNT(*) AS total_requests,
                SUM(input_tokens) AS input_tokens,
                SUM(output_tokens) AS output_tokens,
                SUM(CASE WHEN request_status = 'error' THEN 1 ELSE 0 END) AS total_errors,
                AVG(response_time_ms) AS avg_response_time
            FROM ai_usage_record
            WHERE request_time >= #{start} AND request_time < #{endExclusive}
            GROUP BY COALESCE(NULLIF(model_name, ''), 'unknown')
            ORDER BY total_tokens DESC
            """)
    List<Map<String, Object>> selectModelStats(@Param("start") LocalDateTime start,
                                               @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            SELECT
                DATE(request_time) AS stat_date,
                COUNT(*) AS total_requests,
                SUM(total_tokens) AS total_tokens,
                SUM(input_tokens) AS input_tokens,
                SUM(output_tokens) AS output_tokens,
                SUM(CASE WHEN request_status = 'error' THEN 1 ELSE 0 END) AS total_errors,
                AVG(response_time_ms) AS avg_response_time
            FROM ai_usage_record
            WHERE request_time >= #{start} AND request_time < #{endExclusive}
            GROUP BY DATE(request_time)
            ORDER BY stat_date ASC
            """)
    List<Map<String, Object>> selectDailyStats(@Param("start") LocalDateTime start,
                                               @Param("endExclusive") LocalDateTime endExclusive);

    @Select("""
            <script>
            SELECT
                user_id,
                SUM(total_tokens) AS total_tokens,
                COUNT(*) AS total_requests,
                SUM(input_tokens) AS input_tokens,
                SUM(output_tokens) AS output_tokens
            FROM ai_usage_record
            <if test="start != null and endExclusive != null">
                WHERE request_time >= #{start} AND request_time &lt; #{endExclusive}
            </if>
            GROUP BY user_id
            ORDER BY total_tokens DESC
            LIMIT #{limit}
            </script>
            """)
    List<Map<String, Object>> selectUserTokenRanking(@Param("start") LocalDateTime start,
                                                     @Param("endExclusive") LocalDateTime endExclusive,
                                                     @Param("limit") int limit);

    @Select("""
            <script>
            SELECT userId AS user_id, COUNT(*) AS app_count
            FROM app
            WHERE userId IN
            <foreach collection="userIds" item="userId" open="(" separator="," close=")">
                #{userId}
            </foreach>
            GROUP BY userId
            </script>
            """)
    List<Map<String, Object>> selectAppCountsByUserIds(@Param("userIds") Collection<Long> userIds);
}
