package com.game.vo;

import lombok.Data;

@Data
public class FriendVO {
    private Long id;          // 好友用户ID
    private String name;      // 用户名（username）
    private String avatar;    // 头像
    private Integer level;    // 等级
    private Boolean online;   // 是否在线
    private String currentGame; // 默认 null
    private String message;   // 添加好友请求信息
    private int status;
}