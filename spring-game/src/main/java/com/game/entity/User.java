package com.game.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 用户表
 */
@Data
@EqualsAndHashCode(callSuper = false)
@Accessors(chain = true)
@TableName("user")
public class User {

    /**
     * 用户ID
     */
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    /**
     * 用户名
     */
    @TableField("username")
    private String username;

    /**
     * 邮箱地址
     */
    @TableField("email")
    private String email;

    /**
     * 加密后的密码
     */
    @TableField(value = "password", select = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    /**
     * 头像URL
     */
    @TableField("avatar")
    private String avatar;

    /**
     * 总积分
     */
    @TableField("total_score")
    private Long totalScore;

    /**
     * 当前等级
     */
    @TableField("current_level")
    private Integer currentLevel;

    /**
     * 状态:1-正常,0-禁用
     */
    @TableField("status")
    private Integer status;

    /**
     * 邮箱验证状态:1-已验证,0-未验证
     */
    @TableField("email_verified")
    private Integer emailVerified;

    /**
     * 邮箱验证码
     */
    @TableField("verification_code")
    private String verificationCode;

    /**
     * 发送验证码的时间
     */
    @TableField("verification_expire_time")
    private LocalDateTime verificationExpireTime;

    /**
     * 最后登录时间
     */
    @TableField("last_login_time")
    private LocalDateTime lastLoginTime;

    /**
     * 创建时间
     */
    @TableField("created_time")
    private LocalDateTime createdTime;

    /**
     * 更新时间
     */
    @TableField("updated_time")
    private LocalDateTime updatedTime;
}