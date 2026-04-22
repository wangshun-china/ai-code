declare namespace API {
  type AppAddRequest = {
    initPrompt?: string
    modelKey?: string
  }

  type AppAdminUpdateRequest = {
    id?: number
    appName?: string
    cover?: string
    priority?: number
  }

  type AppDeployRequest = {
    appId?: number
  }

  type AppDeployResultVO = {
    taskId?: number
    appId?: number
    deployKey?: string
    deployUrl?: string
    status?: string
  }

  type AppDeployTaskVO = {
    id?: number
    appId?: number
    userId?: number
    status?: string
    currentStep?: string
    deployKey?: string
    deployUrl?: string
    logText?: string
    errorMessage?: string
    retryCount?: number
    startTime?: string
    endTime?: string
    createTime?: string
    updateTime?: string
  }

  type AppAttachmentVO = {
    id?: number
    appId?: number
    fileName?: string
    fileType?: string
    mimeType?: string
    fileSize?: number
    parsedContent?: string
    parseStatus?: string
    errorMessage?: string
    createTime?: string
  }

  type AppGeneratePlanRequest = {
    appId?: number
    message?: string
  }

  type AppGenerationPlanVO = {
    appId?: number
    planId?: string
    message?: string
    plan?: string
  }

  type AppSourceFileNodeVO = {
    name?: string
    path?: string
    directory?: boolean
    size?: number
    children?: AppSourceFileNodeVO[]
  }

  type AppSourceFileContentVO = {
    name?: string
    path?: string
    content?: string
    language?: string
    size?: number
  }

  type AppQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    deployKey?: string
    status?: string
    priority?: number
    userId?: number
  }

  type AppUpdateRequest = {
    id?: number
    appName?: string
    modelKey?: string
  }

  type AppVO = {
    id?: number
    appName?: string
    cover?: string
    initPrompt?: string
    codeGenType?: string
    modelKey?: string
    deployKey?: string
    deployedTime?: string
    status?: string
    priority?: number
    userId?: number
    createTime?: string
    updateTime?: string
    user?: UserVO
  }

  type BaseResponseAppVO = {
    code?: number
    data?: AppVO
    message?: string
  }

  type BaseResponseAppDeployResultVO = {
    code?: number
    data?: AppDeployResultVO
    message?: string
  }

  type BaseResponseAppDeployTaskVO = {
    code?: number
    data?: AppDeployTaskVO
    message?: string
  }

  type BaseResponseAppGenerationPlanVO = {
    code?: number
    data?: AppGenerationPlanVO
    message?: string
  }

  type BaseResponseAppAttachmentVO = {
    code?: number
    data?: AppAttachmentVO
    message?: string
  }

  type BaseResponseAppAttachmentVOArray = {
    code?: number
    data?: AppAttachmentVO[]
    message?: string
  }

  type BaseResponseAppSourceFileNodeVOArray = {
    code?: number
    data?: AppSourceFileNodeVO[]
    message?: string
  }

  type BaseResponseAppSourceFileContentVO = {
    code?: number
    data?: AppSourceFileContentVO
    message?: string
  }

  type BaseResponseBoolean = {
    code?: number
    data?: boolean
    message?: string
  }

  type BaseResponseLoginUserVO = {
    code?: number
    data?: LoginUserVO
    message?: string
  }

  type BaseResponseLong = {
    code?: number
    data?: number
    message?: string
  }

  type BaseResponsePageAppVO = {
    code?: number
    data?: PageAppVO
    message?: string
  }

  type BaseResponsePageChatHistory = {
    code?: number
    data?: PageChatHistory
    message?: string
  }

  type BaseResponsePageUserVO = {
    code?: number
    data?: PageUserVO
    message?: string
  }

  type BaseResponseString = {
    code?: number
    data?: string
    message?: string
  }

  type BaseResponseUser = {
    code?: number
    data?: User
    message?: string
  }

  type BaseResponseUserVO = {
    code?: number
    data?: UserVO
    message?: string
  }

  type ChatHistory = {
    id?: number
    message?: string
    messageType?: string
    appId?: number
    userId?: number
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type ChatHistoryQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    message?: string
    messageType?: string
    appId?: number
    userId?: number
    lastCreateTime?: string
  }

  type chatToGenCodeParams = {
    appId: number
    message: string
    planId?: string
  }

  type DeleteRequest = {
    id?: number
  }

  type downloadAppCodeParams = {
    appId: number
  }

  type getAppVOByIdByAdminParams = {
    id: number
  }

  type getAppVOByIdParams = {
    id: number
  }

  type getDeployTaskParams = {
    taskId: number
  }

  type uploadAppAttachmentParams = {
    appId: number
  }

  type listAppAttachmentsParams = {
    appId: number
  }

  type listAppSourceFilesParams = {
    appId: number
  }

  type getAppSourceFileContentParams = {
    appId: number
    path: string
  }

  type getUserByIdParams = {
    id: number
  }

  type getUserVOByIdParams = {
    id: number
  }

  type listAppChatHistoryParams = {
    appId: number
    pageSize?: number
    lastCreateTime?: string
  }

  type LoginUserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
    updateTime?: string
  }

  type PageAppVO = {
    records?: AppVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageChatHistory = {
    records?: ChatHistory[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageUserVO = {
    records?: UserVO[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type ServerSentEventString = true

  type serveStaticResourceParams = {
    deployKey: string
  }

  type User = {
    id?: number
    userAccount?: string
    userPassword?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    editTime?: string
    createTime?: string
    updateTime?: string
    isDelete?: number
  }

  type UserAddRequest = {
    userName?: string
    userAccount?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserLoginRequest = {
    userAccount?: string
    userPassword?: string
  }

  type UserQueryRequest = {
    pageNum?: number
    pageSize?: number
    sortField?: string
    sortOrder?: string
    id?: number
    userName?: string
    userAccount?: string
    userProfile?: string
    userRole?: string
  }

  type UserRegisterRequest = {
    userAccount?: string
    userPassword?: string
    checkPassword?: string
  }

  type UserUpdateRequest = {
    id?: number
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
  }

  type UserVO = {
    id?: number
    userAccount?: string
    userName?: string
    userAvatar?: string
    userProfile?: string
    userRole?: string
    createTime?: string
  }
}
