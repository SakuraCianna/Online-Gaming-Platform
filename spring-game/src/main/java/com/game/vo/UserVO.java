package com.game.vo;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserVO {
    private long id;
    private String username;
    private String avatar;
    private long total_score;
    private int current_level;
    private int status;
    private LocalDateTime create_time;
    private LocalDateTime last_login_time;
}
