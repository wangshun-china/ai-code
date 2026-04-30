package com.ws.codecraft.model.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * App generation plan.
 */
@Data
public class AppGenerationPlanVO implements Serializable {

    private Long appId;

    private String planId;

    private String message;

    private String plan;

    private String requirementSummary;

    private List<String> pages;

    private String visualStyle;

    private List<String> components;

    private List<String> filesToChange;

    private List<String> interactions;

    private List<String> acceptanceCriteria;

    private List<String> risks;

    private List<String> questions;

    private List<String> matchedTemplates;

    private static final long serialVersionUID = 1L;
}
