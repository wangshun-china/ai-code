package com.ws.springAi.ai;

import com.yupi.yuaicodemother.model.enums.CodeGenTypeEnum;

public interface AiCodeGenTypeRoutingServiceSpringAI {
    /**
     * 根据用户需求智能选择代码生成类型
     *
     * @param userPrompt 用户输入的需求描述
     * @return 推荐的代码生成类型
     */
    CodeGenTypeEnum routeCodeGenType(String userPrompt);
}
