package com.ws.codecraft.controller;

import com.ws.codecraft.common.BaseResponse;
import com.ws.codecraft.common.DeleteRequest;
import com.ws.codecraft.common.ResultUtils;
import com.ws.codecraft.exception.ErrorCode;
import com.ws.codecraft.exception.ThrowUtils;
import com.ws.codecraft.innerservice.InnerUserService;
import com.ws.codecraft.model.entity.User;
import com.ws.codecraft.model.vo.AppAttachmentVO;
import com.ws.codecraft.service.AppAttachmentService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * App attachment APIs.
 */
@RestController
@RequestMapping("/api/app/attachment")
public class AppAttachmentController {

    @Resource
    private AppAttachmentService appAttachmentService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public BaseResponse<AppAttachmentVO> uploadAttachment(@RequestParam Long appId,
                                                          @RequestPart("file") MultipartFile file,
                                                          HttpServletRequest request) {
        User loginUser = InnerUserService.getLoginUser(request);
        return ResultUtils.success(appAttachmentService.uploadAttachment(appId, file, loginUser));
    }

    @GetMapping("/list")
    public BaseResponse<List<AppAttachmentVO>> listAttachments(@RequestParam Long appId,
                                                               HttpServletRequest request) {
        User loginUser = InnerUserService.getLoginUser(request);
        return ResultUtils.success(appAttachmentService.listAttachmentVO(appId, loginUser));
    }

    @PostMapping("/delete")
    public BaseResponse<Boolean> deleteAttachment(@RequestBody DeleteRequest deleteRequest,
                                                  HttpServletRequest request) {
        ThrowUtils.throwIf(deleteRequest == null || deleteRequest.getId() == null,
                ErrorCode.PARAMS_ERROR, "附件 ID 不能为空");
        User loginUser = InnerUserService.getLoginUser(request);
        return ResultUtils.success(appAttachmentService.deleteAttachment(deleteRequest.getId(), loginUser));
    }
}
