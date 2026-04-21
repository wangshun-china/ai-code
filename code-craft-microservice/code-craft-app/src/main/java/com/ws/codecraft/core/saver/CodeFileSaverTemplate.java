package com.ws.codecraft.core.saver;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.ws.codecraft.config.CodeProjectProperties;
import com.ws.codecraft.exception.BusinessException;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.model.enums.CodeGenTypeEnum;
import com.ws.codecraft.utils.SpringContextUtil;

import java.io.File;
import java.nio.charset.StandardCharsets;

/**
 * 抽象代码文件保存器，模板方法模式。
 */
public abstract class CodeFileSaverTemplate<T> {

    public final File saveCode(T result, Long appId) {
        validateInput(result);
        String baseDirPath = buildUniqueDir(appId);
        saveFiles(result, baseDirPath);
        return new File(baseDirPath);
    }

    public final void writeToFile(String dirPath, String filename, String content) {
        if (StrUtil.isNotBlank(content)) {
            String filePath = dirPath + File.separator + filename;
            FileUtil.writeString(content, filePath, StandardCharsets.UTF_8);
        }
    }

    protected void validateInput(T result) {
        if (result == null) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "代码结果对象不能为空");
        }
    }

    protected String buildUniqueDir(Long appId) {
        if (appId == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "应用 ID 不能为空");
        }
        String codeType = getCodeType().getValue();
        String uniqueDirName = StrUtil.format("{}_{}", codeType, appId);
        CodeProjectProperties properties = SpringContextUtil.getBean(CodeProjectProperties.class);
        String dirPath = properties.getOutputRootDir() + File.separator + uniqueDirName;
        FileUtil.mkdir(dirPath);
        return dirPath;
    }

    protected abstract void saveFiles(T result, String baseDirPath);

    protected abstract CodeGenTypeEnum getCodeType();
}
