package com.ws.codecraft.model.entity;

import com.mybatisflex.annotation.Column;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.KeyType;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户 AI 模型凭据配置。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table("ai_model_credential")
public class AiModelCredential implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Id(keyType = KeyType.Auto)
    private Long id;

    @Column("userId")
    private Long userId;

    private String name;

    @Column("apiKey")
    private String apiKey;

    @Column("baseUrl")
    private String baseUrl;

    @Column("modelNames")
    private String modelNames;

    @Column("isDefault")
    private Integer isDefault;

    @Column("systemDefault")
    private Integer systemDefault;

    @Column("createTime")
    private LocalDateTime createTime;

    @Column("updateTime")
    private LocalDateTime updateTime;

    @Column(value = "isDelete", isLogicDelete = true)
    private Integer isDelete;
}
