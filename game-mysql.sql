SET NAMES utf8mb4;

SET FOREIGN_KEY_CHECKS = 0;

-- ==================== 删除现有表（如果存在）====================
DROP TABLE IF EXISTS friend_chat;

DROP TABLE IF EXISTS friend;

DROP TABLE IF EXISTS game2048;

DROP TABLE IF EXISTS gomoku_game;

DROP TABLE IF EXISTS minesweeper;

DROP TABLE IF EXISTS room_player;

DROP TABLE IF EXISTS game_room;

DROP TABLE IF EXISTS tank_battle;

DROP TABLE IF EXISTS test;

DROP TABLE IF EXISTS `user`;

-- ==================== 用户表 ====================
CREATE TABLE `user` (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '用户ID',
    username VARCHAR(50) NOT NULL COMMENT '用户名',
    email VARCHAR(100) NOT NULL COMMENT '邮箱地址',
    password VARCHAR(255) COMMENT '加密后的密码',
    avatar VARCHAR(255) COMMENT '头像URL',
    total_score BIGINT DEFAULT 0 COMMENT '总积分',
    current_level INT DEFAULT 1 COMMENT '当前等级',
    status TINYINT DEFAULT 1 COMMENT '状态:1-正常,0-禁用',
    email_verified TINYINT DEFAULT 0 COMMENT '邮箱验证状态:1-已验证,0-未验证',
    verification_code VARCHAR(100) COMMENT '邮箱验证码',
    verification_expire_time DATETIME COMMENT '发送验证码的时间',
    last_login_time DATETIME COMMENT '最后登录时间',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    UNIQUE KEY uk_username (username),
    UNIQUE KEY uk_email (email),
    KEY idx_status_user (status),
    KEY idx_total_score (total_score),
    KEY idx_current_level (current_level),
    KEY idx_last_login_time (last_login_time),
    KEY idx_created_time_user (created_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表';

-- ==================== 好友关系表 ====================
CREATE TABLE friend (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '好友关系ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    friend_id BIGINT NOT NULL COMMENT '好友ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    message VARCHAR(255) COMMENT '请求消息',
    status TINYINT DEFAULT 0 COMMENT '状态:0-待处理,1-已接受,2-已拒绝',
    sort_rank TINYINT AS (
        CASE status
            WHEN 0 THEN 0
            WHEN 1 THEN 1
            WHEN 2 THEN 2
            ELSE 3
        END
    ) STORED COMMENT '排序字段',
    UNIQUE KEY uk_user_friend (user_id, friend_id),
    KEY idx_user_id_friend (user_id),
    KEY idx_friend_id (friend_id),
    KEY idx_status_friend (status),
    KEY idx_created_time_friend (created_time),
    KEY idx_friend_sort (created_time DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '好友关系表';

-- ==================== 好友聊天消息表 ====================
CREATE TABLE friend_chat (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '消息ID',
    sender_id BIGINT NOT NULL COMMENT '发送者用户ID',
    receiver_id BIGINT NOT NULL COMMENT '接收者用户ID',
    content TEXT NOT NULL COMMENT '消息内容',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    is_read TINYINT NOT NULL DEFAULT 0 COMMENT '是否已读，0未读，1已读'
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '好友聊天消息表';

-- ==================== 2048游戏记录表 ====================
CREATE TABLE game2048 (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '游戏ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    difficulty VARCHAR(20) DEFAULT 'normal' COMMENT '难度:easy-简单,normal-普通,hard-困难',
    max_tile INT DEFAULT 2 COMMENT '最大方块数值',
    moves_count INT DEFAULT 0 COMMENT '移动步数',
    duration INT DEFAULT 0 COMMENT '游戏时长(秒)',
    status TINYINT DEFAULT 0 COMMENT '状态:0-进行中,1-通关,2-失败',
    game_data JSON COMMENT '游戏状态数据(JSON格式)',
    score INT DEFAULT 0 COMMENT '得分',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_id_2048 (user_id),
    KEY idx_difficulty_2048 (difficulty),
    KEY idx_max_tile (max_tile),
    KEY idx_status_2048 (status),
    KEY idx_user_status_2048 (user_id, status),
    KEY idx_user_difficulty_2048 (user_id, difficulty),
    KEY idx_user_create_time_2048 (user_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '2048游戏记录表';

-- ==================== 游戏房间表 ====================
CREATE TABLE game_room (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '房间ID',
    room_code VARCHAR(20) NOT NULL COMMENT '房间码',
    game_name VARCHAR(128) NOT NULL COMMENT '游戏名称',
    players INT DEFAULT 0 COMMENT '当前玩家数',
    max_players INT DEFAULT 2 COMMENT '最大玩家数',
    min_players INT DEFAULT 2 COMMENT '最小玩家数',
    status TINYINT DEFAULT 0 COMMENT '状态:0-等待中,1-游戏中,2-已结束',
    team_mode TINYINT DEFAULT 0 COMMENT '队伍模式:0-无队伍,1-1v1,2-2v2,3-3v3,4-自由队伍',
    game_config JSON COMMENT '游戏配置(如棋盘大小、地图、规则等)',
    winner_id BIGINT COMMENT '胜利者ID',
    creator_id BIGINT NOT NULL COMMENT '创建者ID',
    is_private TINYINT DEFAULT 0 COMMENT '是否私密房间:1-是,0-否',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    UNIQUE KEY uk_room_code (room_code),
    KEY idx_game_name (game_name),
    KEY idx_status_room (status),
    KEY idx_creator_id (creator_id),
    KEY idx_winner_id_room (winner_id),
    KEY idx_team_mode (team_mode),
    KEY idx_start_time_room (start_time),
    KEY idx_end_time_room (end_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '游戏房间表';

-- ==================== 五子棋对局记录表 ====================
CREATE TABLE gomoku_game (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '对局ID',
    room_id BIGINT COMMENT '房间ID(和房间表关联)',
    player1_id BIGINT COMMENT '玩家1用户ID(先手)',
    player2_id BIGINT COMMENT '玩家2用户ID(后手)',
    winner_id BIGINT COMMENT '胜者用户ID,平局为NULL',
    loser_id BIGINT COMMENT '败者用户ID,平局为NULL',
    is_draw TINYINT DEFAULT 0 COMMENT '是否平局: 1-平局, 0-非平局',
    move_count INT DEFAULT 0 COMMENT '总步数',
    duration INT DEFAULT 0 COMMENT '用时(秒)',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    game_data JSON COMMENT '可选,存最终棋盘状态或回放',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_player1_id (player1_id),
    KEY idx_player2_id (player2_id),
    KEY idx_winner_id_gomoku (winner_id),
    KEY idx_start_time_gomoku (start_time),
    KEY idx_end_time_gomoku (end_time),
    KEY idx_player1_create_time (player1_id, create_time),
    KEY idx_player2_create_time (player2_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '五子棋对局记录表';

-- ==================== 扫雷游戏记录表 ====================
CREATE TABLE minesweeper (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '游戏ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    difficulty VARCHAR(20) NOT NULL COMMENT '难度:beginner-初级,intermediate-中级,expert-高级,custom-自定义',
    board_width INT DEFAULT 9 COMMENT '棋盘宽度',
    board_height INT DEFAULT 9 COMMENT '棋盘高度',
    mine_count INT DEFAULT 10 COMMENT '地雷数量',
    correct_flags INT NOT NULL DEFAULT 0 COMMENT '正确排雷数',
    duration INT DEFAULT 0 COMMENT '用时(秒)',
    status TINYINT DEFAULT 0 COMMENT '状态:0-进行中,1-胜利,2-失败',
    game_data JSON COMMENT '游戏状态数据(JSON格式)',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_id_ms (user_id),
    KEY idx_difficulty_ms (difficulty),
    KEY idx_status_ms (status),
    KEY idx_user_status_ms (user_id, status),
    KEY idx_user_difficulty_ms (user_id, difficulty),
    KEY idx_user_create_time_ms (user_id, create_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '扫雷游戏记录表';

-- ==================== 房间玩家表 ====================
CREATE TABLE room_player (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '记录ID',
    room_id BIGINT COMMENT '房间ID(与game_room关联)',
    user_id BIGINT COMMENT '用户ID,NULL为AI玩家',
    is_ai TINYINT DEFAULT 0 COMMENT '是否AI玩家:1-是,0-否',
    ai_type VARCHAR(20) COMMENT 'AI类型:simple-简单,advanced-高级,custom-自定义',
    ai_config JSON COMMENT 'AI配置参数(如难度、行为策略等)',
    team_id INT DEFAULT 0 COMMENT '队伍ID:0-无队伍,1-队伍A,2-队伍B,依此类推',
    is_ready TINYINT DEFAULT 0 COMMENT '是否准备:1-已准备,0-未准备',
    player_role VARCHAR(20) DEFAULT 'member' COMMENT '玩家角色:owner-房主,captain-队长,member-队员',
    position INT DEFAULT 0 COMMENT '位置编号:用于记录玩家座位/顺序',
    `kill` INT DEFAULT 0 COMMENT '击杀数',
    `death` INT DEFAULT 0 COMMENT '死亡次数',
    `assist` INT DEFAULT 0 COMMENT '协助数',
    join_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
    leave_time DATETIME COMMENT '离开时间',
    UNIQUE KEY uk_room_user (room_id, user_id),
    KEY idx_room_id (room_id),
    KEY idx_user_id_rp (user_id),
    KEY idx_is_ai (is_ai),
    KEY idx_team_id (team_id),
    KEY idx_is_ready (is_ready),
    KEY idx_player_role (player_role),
    KEY idx_position (position),
    KEY idx_join_time (join_time)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '房间玩家表';

-- ==================== 坦克大战游戏记录表 ====================
CREATE TABLE tank_battle (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '游戏ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    room_id BIGINT COMMENT '房间ID',
    map VARCHAR(255) DEFAULT '/resource/maps/tankmap-1.json' COMMENT '地图静态地址',
    game_data JSON COMMENT '游戏数据(地图配置、障碍物、回放等)',
    kills INT DEFAULT 0 COMMENT '击杀数',
    deaths INT DEFAULT 0 COMMENT '死亡次数',
    assists INT DEFAULT 0 COMMENT '助攻数',
    damage_dealt INT DEFAULT 0 COMMENT '造成伤害',
    damage_taken INT DEFAULT 0 COMMENT '受到伤害',
    score INT DEFAULT 0 COMMENT '得分(用于排行榜)',
    final_rank INT COMMENT '最终排名',
    duration INT DEFAULT 0 COMMENT '游戏时长(秒)',
    status TINYINT DEFAULT 0 COMMENT '状态:0-进行中,1-已结束',
    start_time DATETIME COMMENT '开始时间',
    end_time DATETIME COMMENT '结束时间',
    create_time DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    KEY idx_user_id_tb (user_id),
    KEY idx_room_id_tb (room_id),
    KEY idx_map (map),
    KEY idx_score_tb (score DESC),
    KEY idx_user_score_tb (user_id, score DESC),
    KEY idx_status_tb (status),
    KEY idx_create_time_tb (create_time DESC)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '坦克大战游戏记录表';

SET FOREIGN_KEY_CHECKS = 1;