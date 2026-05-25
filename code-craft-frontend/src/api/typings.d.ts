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

  type AppChatRequest = {
    appId?: number
    message?: string
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
    requirementSummary?: string
    pages?: string[]
    visualStyle?: string
    components?: string[]
    filesToChange?: string[]
    interactions?: string[]
    acceptanceCriteria?: string[]
    risks?: string[]
    questions?: string[]
    matchedTemplates?: string[]
  }

  type AppGenerationQualityVO = {
    taskId?: number
    appId?: number
    userId?: number
    modelKey?: string
    codeGenType?: string
    status?: string
    userMessage?: string
    aiOutputPreview?: string
    qualityMetrics?: AppGenerationQualityMetrics
    createTime?: string
    startTime?: string
    endTime?: string
  }

  type AppGenerationQualityMetrics = {
    appId?: number
    codeGenType?: string
    modelKey?: string
    usedPlan?: boolean
    streamChunkCount?: number
    generatedCharCount?: number
    durationMs?: number
    sourceDirExists?: boolean
    generatedFileCount?: number
    buildSuccess?: boolean
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

  type AiModelVO = {
    value?: string
    text?: string
    endpoint?: string
    baseUrl?: string
    multimodal?: boolean
    custom?: boolean
  }

  type AiModelCredentialVO = {
    id?: number
    name?: string
    apiKey?: string
    baseUrl?: string
    modelNames?: string[]
    defaultSelected?: boolean
    systemDefault?: boolean
  }

  type AiModelCredentialRequest = {
    id?: number
    name?: string
    apiKey?: string
    baseUrl?: string
    modelNames?: string[]
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

  type BaseResponse<T> = {
    code?: number
    data?: T
    message?: string
  }

  type BaseResponseAppVO = BaseResponse<AppVO>
  type BaseResponseAppDeployResultVO = BaseResponse<AppDeployResultVO>
  type BaseResponseAppDeployTaskVO = BaseResponse<AppDeployTaskVO>
  type BaseResponseAppGenerationPlanVO = BaseResponse<AppGenerationPlanVO>
  type BaseResponseAppAttachmentVO = BaseResponse<AppAttachmentVO>
  type BaseResponseAppAttachmentVOArray = BaseResponse<AppAttachmentVO[]>
  type BaseResponseAppSourceFileNodeVOArray = BaseResponse<AppSourceFileNodeVO[]>
  type BaseResponseAppSourceFileContentVO = BaseResponse<AppSourceFileContentVO>
  type BaseResponseBoolean = BaseResponse<boolean>
  type BaseResponseAiModelVOArray = BaseResponse<AiModelVO[]>
  type BaseResponseAiModelCredentialVOArray = BaseResponse<AiModelCredentialVO[]>
  type BaseResponseLoginUserVO = BaseResponse<LoginUserVO>
  type BaseResponseLong = BaseResponse<number>
  type BaseResponsePageAppVO = BaseResponse<PageResult<AppVO>>
  type BaseResponsePageChatHistory = BaseResponse<PageResult<ChatHistory>>
  type BaseResponsePageUserVO = BaseResponse<PageResult<UserVO>>
  type BaseResponseString = BaseResponse<string>
  type BaseResponseUser = BaseResponse<User>
  type BaseResponseUserVO = BaseResponse<UserVO>

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

  type listAiModelsParams = {
    userId?: number
  }

  type listAiModelCredentialsParams = {
    userId?: number
  }

  type saveAiModelCredentialParams = {
    userId?: number
  }

  type selectAiModelCredentialParams = {
    userId?: number
    credentialId?: number
  }

  type removeAiModelCredentialParams = {
    userId?: number
    credentialId?: number
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

  type PageResult<T> = {
    records?: T[]
    pageNumber?: number
    pageSize?: number
    totalPage?: number
    totalRow?: number
    optimizeCountQuery?: boolean
  }

  type PageAppVO = PageResult<AppVO>
  type PageChatHistory = PageResult<ChatHistory>
  type PageUserVO = PageResult<UserVO>

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
