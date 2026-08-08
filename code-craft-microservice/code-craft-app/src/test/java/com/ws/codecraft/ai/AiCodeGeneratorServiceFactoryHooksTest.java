package com.ws.codecraft.ai;

import com.alibaba.cloud.ai.graph.agent.hook.Hook;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.agent.hook.skills.SkillsAgentHook;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class AiCodeGeneratorServiceFactoryHooksTest {

    @Test
    void commonHooksIncludeSummarizationSkillsAndModelCallLimit() {
        AiCodeGeneratorServiceFactory factory = new AiCodeGeneratorServiceFactory();
        ChatModel model = mock(ChatModel.class);

        @SuppressWarnings("unchecked")
        List<Hook> hooks = (List<Hook>) ReflectionTestUtils.invokeMethod(factory, "commonHooks", model);

        assertEquals(3, hooks.size());
        assertTrue(hooks.stream().anyMatch(h -> h instanceof SummarizationHook),
                "应包含上下文压缩 SummarizationHook");
        assertTrue(hooks.stream().anyMatch(h -> h instanceof SkillsAgentHook),
                "应包含 Skills 渐进披露 SkillsAgentHook");
        assertTrue(hooks.stream().anyMatch(h -> h instanceof ModelCallLimitHook),
                "应包含模型调用上限 ModelCallLimitHook");
    }
}
