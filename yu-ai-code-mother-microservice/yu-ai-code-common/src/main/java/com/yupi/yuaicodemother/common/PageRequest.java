package com.yupi.yuaicodemother.common;

import lombok.Data;

import java.io.Serializable;

/**
 * 请求封装类
 */
@Data
public class PageRequest implements Serializable {

    private int pageNum = 1;

    private int pageSize = 10;

    private String sortField;

    private String sortOrder = "descend";

    private static final long serialVersionUID = 1L;
}