package com.ws.codecraft.service.impl;

import com.ws.codecraft.innerservice.InnerUserService;
import com.ws.codecraft.mapper.AppMapper;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.vo.AiMetricsVO;
import com.ws.codecraft.service.AiDailyStatisticsService;
import com.ws.codecraft.service.AiUsageRecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StatisticsServiceImplTest {

    @Mock
    private AppMapper appMapper;

    @Mock
    private AiUsageRecordService aiUsageRecordService;

    @Mock
    private AiDailyStatisticsService aiDailyStatisticsService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private InnerUserService userService;

    @InjectMocks
    private StatisticsServiceImpl statisticsService;

    private Map<String, Object> totalMetrics;

    @BeforeEach
    void setUp() {
        totalMetrics = Map.of(
                "total_tokens", 120L,
                "input_tokens", 40L,
                "output_tokens", 80L,
                "total_requests", 5L,
                "total_errors", 1L,
                "avg_response_time", 210.0
        );
    }

    @Test
    void getAiMetrics_shouldApplyDateRangeAndBatchLoadUsers() {
        doReturn(totalMetrics).when(jdbcTemplate).queryForMap(
                contains("FROM ai_usage_record"),
                any(LocalDateTime.class),
                any(LocalDateTime.class));
        doReturn(List.of(
                Map.of(
                        "stat_date", Timestamp.valueOf(LocalDateTime.of(2026, 4, 20, 0, 0)),
                        "total_requests", 3L,
                        "total_tokens", 100L,
                        "input_tokens", 30L,
                        "output_tokens", 70L,
                        "total_errors", 1L,
                        "avg_response_time", 180.0
                )
        )).doReturn(List.of(
                Map.of(
                        "model_name", "qwen3.5-plus",
                        "total_requests", 3L,
                        "total_tokens", 100L,
                        "input_tokens", 30L,
                        "output_tokens", 70L,
                        "total_errors", 1L,
                        "avg_response_time", 180.0
                )
        )).when(jdbcTemplate).queryForList(any(String.class), any(LocalDateTime.class), any(LocalDateTime.class));
        doReturn(List.of(
                Map.of(
                        "user_id", "1",
                        "total_tokens", 100L,
                        "total_requests", 3L,
                        "input_tokens", 30L,
                        "output_tokens", 70L
                )
        )).when(jdbcTemplate).queryForList(
                any(String.class),
                any(LocalDateTime.class),
                any(LocalDateTime.class),
                any(Integer.class));
        doReturn(List.of(Map.of("user_id", 1L, "app_count", 2L))).when(jdbcTemplate).queryForList(
                eq("SELECT user_id, COUNT(*) AS app_count FROM app WHERE user_id IN (?) GROUP BY user_id"),
                any(Object[].class));
        when(userService.listByIds(List.of(1L))).thenReturn(List.of(User.builder().id(1L).userName("ws").build()));

        AiMetricsVO result = statisticsService.getAiMetrics("2026-04-20", "2026-04-21");

        assertNotNull(result);
        assertEquals(5L, result.getTotalRequests());
        assertEquals(20.0, result.getErrorRate());
        assertEquals(1, result.getUserStats().size());
        assertEquals("ws", result.getUserStats().getFirst().getUserName());
        assertEquals(2, result.getUserStats().getFirst().getAppCount());

        verify(userService).listByIds(List.of(1L));
        verify(userService, never()).getById(any());

        ArgumentCaptor<Object> startCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> endCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate).queryForMap(any(String.class), startCaptor.capture(), endCaptor.capture());
        assertTrue(startCaptor.getValue().toString().contains("2026-04-20T00:00"));
        assertTrue(endCaptor.getValue().toString().contains("2026-04-22T00:00"));
    }
}
