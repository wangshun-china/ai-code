package com.ws.codecraft.service;

import com.mybatisflex.core.service.IService;
import com.ws.codecraft.model.entity.AppAttachment;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.vo.AppAttachmentVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * App attachment service.
 */
public interface AppAttachmentService extends IService<AppAttachment> {

    AppAttachmentVO uploadAttachment(Long appId, MultipartFile file, User loginUser);

    List<AppAttachmentVO> listAttachmentVO(Long appId, User loginUser);

    boolean deleteAttachment(Long attachmentId, User loginUser);

    String buildAttachmentContext(Long appId, Long userId);
}
