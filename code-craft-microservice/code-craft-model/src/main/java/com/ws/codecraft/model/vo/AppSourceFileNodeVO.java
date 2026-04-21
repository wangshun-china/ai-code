package com.ws.codecraft.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * Generated source file tree node.
 */
@Data
public class AppSourceFileNodeVO implements Serializable {

    private String name;

    private String path;

    private Boolean directory;

    private Long size;

    private List<AppSourceFileNodeVO> children;

    private static final long serialVersionUID = 1L;
}
