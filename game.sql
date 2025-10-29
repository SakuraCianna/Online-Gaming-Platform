SET client_encoding = 'UTF8';

-- ==================== 删除现有表（如果存在）====================
DROP TABLE IF EXISTS friend_chat CASCADE;

DROP TABLE IF EXISTS friend CASCADE;

DROP TABLE IF EXISTS game2048 CASCADE;

DROP TABLE IF EXISTS gomoku_game CASCADE;

DROP TABLE IF EXISTS minesweeper CASCADE;

DROP TABLE IF EXISTS room_player CASCADE;

DROP TABLE IF EXISTS game_room CASCADE;

DROP TABLE IF EXISTS tank_battle CASCADE;

DROP TABLE IF EXISTS test CASCADE;

DROP TABLE IF EXISTS "user" CASCADE;

-- ==================== 用户表 ====================
CREATE TABLE "user" (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL,
    password VARCHAR(255),
    avatar VARCHAR(255),
    total_score BIGINT DEFAULT 0,
    current_level INT DEFAULT 1,
    status SMALLINT DEFAULT 1, -- 1-正常,0-禁用
    email_verified SMALLINT DEFAULT 0, -- 1-已验证,0-未验证
    verification_code VARCHAR(100),
    verification_expire_time TIMESTAMP,
    last_login_time TIMESTAMP,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_username UNIQUE (username),
    CONSTRAINT uk_email UNIQUE (email)
);

-- 用户表索引
CREATE INDEX idx_status_user ON "user" (status);

CREATE INDEX idx_total_score ON "user" (total_score);

CREATE INDEX idx_current_level ON "user" (current_level);

CREATE INDEX idx_last_login_time ON "user" (last_login_time);

CREATE INDEX idx_created_time_user ON "user" (created_time);

COMMENT ON TABLE "user" IS '用户表';

COMMENT ON COLUMN "user".id IS '用户ID';

COMMENT ON COLUMN "user".username IS '用户名';

COMMENT ON COLUMN "user".email IS '邮箱地址';

COMMENT ON COLUMN "user".password IS '加密后的密码';

COMMENT ON COLUMN "user".avatar IS '头像URL';

COMMENT ON COLUMN "user".total_score IS '总积分';

COMMENT ON COLUMN "user".current_level IS '当前等级';

COMMENT ON COLUMN "user".status IS '状态:1-正常,0-禁用';

COMMENT ON COLUMN "user".email_verified IS '邮箱验证状态:1-已验证,0-未验证';

COMMENT ON COLUMN "user".verification_code IS '邮箱验证码';

COMMENT ON COLUMN "user".verification_expire_time IS '发送验证码的时间';

COMMENT ON COLUMN "user".last_login_time IS '最后登录时间';

COMMENT ON COLUMN "user".created_time IS '创建时间';

COMMENT ON COLUMN "user".updated_time IS '更新时间';

-- ==================== 好友关系表 ====================
CREATE TABLE friend (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    friend_id BIGINT NOT NULL,
    created_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    message VARCHAR(255),
    status SMALLINT DEFAULT 0, -- 0-待处理,1-已接受,2-已拒绝
    sort_rank SMALLINT GENERATED ALWAYS AS (
        CASE status
            WHEN 0 THEN 0
            WHEN 1 THEN 1
            WHEN 2 THEN 2
            ELSE 3
        END
    ) STORED,
    CONSTRAINT uk_user_friend UNIQUE (user_id, friend_id)
);

-- 好友表索引
CREATE INDEX idx_user_id_friend ON friend (user_id);

CREATE INDEX idx_friend_id ON friend (friend_id);

CREATE INDEX idx_status_friend ON friend (status);

CREATE INDEX idx_created_time_friend ON friend (created_time);

CREATE INDEX idx_friend_sort ON friend (created_time DESC);

COMMENT ON TABLE friend IS '好友关系表';

COMMENT ON COLUMN friend.user_id IS '用户ID';

COMMENT ON COLUMN friend.friend_id IS '好友ID';

COMMENT ON COLUMN friend.created_time IS '创建时间';

COMMENT ON COLUMN friend.message IS '请求消息';

COMMENT ON COLUMN friend.status IS '状态:0-待处理,1-已接受,2-已拒绝';

-- ==================== 好友聊天消息表 ====================
CREATE TABLE friend_chat (
    id BIGSERIAL PRIMARY KEY,
    sender_id BIGINT NOT NULL,
    receiver_id BIGINT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    is_read SMALLINT NOT NULL DEFAULT 0 -- 0未读,1已读
);

COMMENT ON TABLE friend_chat IS '好友聊天消息表';

COMMENT ON COLUMN friend_chat.id IS '消息ID';

COMMENT ON COLUMN friend_chat.sender_id IS '发送者用户ID';

COMMENT ON COLUMN friend_chat.receiver_id IS '接收者用户ID';

COMMENT ON COLUMN friend_chat.content IS '消息内容';

COMMENT ON COLUMN friend_chat.created_at IS '发送时间';

COMMENT ON COLUMN friend_chat.is_read IS '是否已读，0未读，1已读';

-- ==================== 2048游戏记录表 ====================
CREATE TABLE game2048 (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    difficulty VARCHAR(20) DEFAULT 'normal', -- easy-简单,normal-普通,hard-困难
    max_tile INT DEFAULT 2,
    moves_count INT DEFAULT 0,
    duration INT DEFAULT 0,
    status SMALLINT DEFAULT 0, -- 0-进行中,1-通关,2-失败
    game_data JSONB,
    score INT DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 2048游戏表索引
CREATE INDEX idx_user_id_2048 ON game2048 (user_id);

CREATE INDEX idx_difficulty_2048 ON game2048 (difficulty);

CREATE INDEX idx_max_tile ON game2048 (max_tile);

CREATE INDEX idx_status_2048 ON game2048 (status);

CREATE INDEX idx_user_status_2048 ON game2048 (user_id, status);

CREATE INDEX idx_user_difficulty_2048 ON game2048 (user_id, difficulty);

CREATE INDEX idx_user_create_time_2048 ON game2048 (user_id, create_time);

COMMENT ON TABLE game2048 IS '2048游戏记录表';

COMMENT ON COLUMN game2048.user_id IS '用户ID';

COMMENT ON COLUMN game2048.difficulty IS '难度:easy-简单,normal-普通,hard-困难';

COMMENT ON COLUMN game2048.max_tile IS '最大方块数值';

COMMENT ON COLUMN game2048.moves_count IS '移动步数';

COMMENT ON COLUMN game2048.duration IS '游戏时长(秒)';

COMMENT ON COLUMN game2048.status IS '状态:0-进行中,1-通关,2-失败';

COMMENT ON COLUMN game2048.game_data IS '游戏状态数据(JSON格式)';

COMMENT ON COLUMN game2048.score IS '得分';

COMMENT ON COLUMN game2048.create_time IS '创建时间';

-- ==================== 游戏房间表 ====================
CREATE TABLE game_room (
    id BIGSERIAL PRIMARY KEY,
    room_code VARCHAR(20) NOT NULL,
    game_name VARCHAR(128) NOT NULL,
    players INT DEFAULT 0,
    max_players INT DEFAULT 2,
    min_players INT DEFAULT 2,
    status SMALLINT DEFAULT 0, -- 0-等待中,1-游戏中,2-已结束
    team_mode SMALLINT DEFAULT 0, -- 0-无队伍,1-1v1,2-2v2,3-3v3,4-自由队伍
    game_config JSONB,
    winner_id BIGINT,
    creator_id BIGINT NOT NULL,
    is_private SMALLINT DEFAULT 0, -- 1-是,0-否
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    CONSTRAINT uk_room_code UNIQUE (room_code)
);

-- 游戏房间表索引
CREATE INDEX idx_game_name ON game_room (game_name);

CREATE INDEX idx_status_room ON game_room (status);

CREATE INDEX idx_creator_id ON game_room (creator_id);

CREATE INDEX idx_winner_id_room ON game_room (winner_id);

CREATE INDEX idx_team_mode ON game_room (team_mode);

CREATE INDEX idx_start_time_room ON game_room (start_time);

CREATE INDEX idx_end_time_room ON game_room (end_time);

COMMENT ON TABLE game_room IS '游戏房间表';

COMMENT ON COLUMN game_room.room_code IS '房间码';

COMMENT ON COLUMN game_room.game_name IS '游戏名称';

COMMENT ON COLUMN game_room.players IS '当前玩家数';

COMMENT ON COLUMN game_room.max_players IS '最大玩家数';

COMMENT ON COLUMN game_room.min_players IS '最小玩家数';

COMMENT ON COLUMN game_room.status IS '状态:0-等待中,1-游戏中,2-已结束';

COMMENT ON COLUMN game_room.team_mode IS '队伍模式:0-无队伍,1-1v1,2-2v2,3-3v3,4-自由队伍';

COMMENT ON COLUMN game_room.game_config IS '游戏配置(如棋盘大小、地图、规则等)';

COMMENT ON COLUMN game_room.winner_id IS '胜利者ID';

COMMENT ON COLUMN game_room.creator_id IS '创建者ID';

COMMENT ON COLUMN game_room.is_private IS '是否私密房间:1-是,0-否';

COMMENT ON COLUMN game_room.start_time IS '开始时间';

COMMENT ON COLUMN game_room.end_time IS '结束时间';

-- ==================== 五子棋对局记录表 ====================
CREATE TABLE gomoku_game (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT,
    player1_id BIGINT,
    player2_id BIGINT,
    winner_id BIGINT,
    loser_id BIGINT,
    is_draw SMALLINT DEFAULT 0, -- 1-平局, 0-非平局
    move_count INT DEFAULT 0,
    duration INT DEFAULT 0,
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    game_data JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 五子棋表索引
CREATE INDEX idx_player1_id ON gomoku_game (player1_id);

CREATE INDEX idx_player2_id ON gomoku_game (player2_id);

CREATE INDEX idx_winner_id_gomoku ON gomoku_game (winner_id);

CREATE INDEX idx_start_time_gomoku ON gomoku_game (start_time);

CREATE INDEX idx_end_time_gomoku ON gomoku_game (end_time);

CREATE INDEX idx_player1_create_time ON gomoku_game (player1_id, create_time);

CREATE INDEX idx_player2_create_time ON gomoku_game (player2_id, create_time);

COMMENT ON TABLE gomoku_game IS '五子棋对局记录表';

COMMENT ON COLUMN gomoku_game.id IS '对局ID';

COMMENT ON COLUMN gomoku_game.room_id IS '房间ID(和房间表关联)';

COMMENT ON COLUMN gomoku_game.player1_id IS '玩家1用户ID(先手)';

COMMENT ON COLUMN gomoku_game.player2_id IS '玩家2用户ID(后手)';

COMMENT ON COLUMN gomoku_game.winner_id IS '胜者用户ID,平局为NULL';

COMMENT ON COLUMN gomoku_game.loser_id IS '败者用户ID,平局为NULL';

COMMENT ON COLUMN gomoku_game.is_draw IS '是否平局: 1-平局, 0-非平局';

COMMENT ON COLUMN gomoku_game.move_count IS '总步数';

COMMENT ON COLUMN gomoku_game.duration IS '用时(秒)';

COMMENT ON COLUMN gomoku_game.start_time IS '开始时间';

COMMENT ON COLUMN gomoku_game.end_time IS '结束时间';

COMMENT ON COLUMN gomoku_game.game_data IS '可选,存最终棋盘状态或回放';

COMMENT ON COLUMN gomoku_game.create_time IS '创建时间';

-- ==================== 扫雷游戏记录表 ====================
CREATE TABLE minesweeper (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    difficulty VARCHAR(20) NOT NULL, -- beginner-初级,intermediate-中级,expert-高级,custom-自定义
    board_width INT DEFAULT 9,
    board_height INT DEFAULT 9,
    mine_count INT DEFAULT 10,
    correct_flags INT NOT NULL DEFAULT 0,
    duration INT DEFAULT 0,
    status SMALLINT DEFAULT 0, -- 0-进行中,1-胜利,2-失败
    game_data JSONB,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 扫雷表索引
CREATE INDEX idx_user_id_ms ON minesweeper (user_id);

CREATE INDEX idx_difficulty_ms ON minesweeper (difficulty);

CREATE INDEX idx_status_ms ON minesweeper (status);

CREATE INDEX idx_user_status_ms ON minesweeper (user_id, status);

CREATE INDEX idx_user_difficulty_ms ON minesweeper (user_id, difficulty);

CREATE INDEX idx_user_create_time_ms ON minesweeper (user_id, create_time);

COMMENT ON TABLE minesweeper IS '扫雷游戏记录表';

COMMENT ON COLUMN minesweeper.user_id IS '用户ID';

COMMENT ON COLUMN minesweeper.difficulty IS '难度:beginner-初级,intermediate-中级,expert-高级,custom-自定义';

COMMENT ON COLUMN minesweeper.board_width IS '棋盘宽度';

COMMENT ON COLUMN minesweeper.board_height IS '棋盘高度';

COMMENT ON COLUMN minesweeper.mine_count IS '地雷数量';

COMMENT ON COLUMN minesweeper.correct_flags IS '正确排雷数';

COMMENT ON COLUMN minesweeper.duration IS '用时(秒)';

COMMENT ON COLUMN minesweeper.status IS '状态:0-进行中,1-胜利,2-失败';

COMMENT ON COLUMN minesweeper.game_data IS '游戏状态数据(JSON格式)';

COMMENT ON COLUMN minesweeper.create_time IS '创建时间';

-- ==================== 房间玩家表 ====================
CREATE TABLE room_player (
    id BIGSERIAL PRIMARY KEY,
    room_id BIGINT,
    user_id BIGINT,
    is_ai SMALLINT DEFAULT 0, -- 1-是,0-否
    ai_type VARCHAR(20),
    ai_config JSONB,
    team_id INT DEFAULT 0,
    is_ready SMALLINT DEFAULT 0, -- 1-已准备,0-未准备
    player_role VARCHAR(20) DEFAULT 'member',
    position INT DEFAULT 0,
    kill INT DEFAULT 0,
    death INT DEFAULT 0,
    assist INT DEFAULT 0,
    join_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    leave_time TIMESTAMP,
    CONSTRAINT uk_room_user UNIQUE (room_id, user_id)
);

-- 房间玩家表索引
CREATE INDEX idx_room_id ON room_player (room_id);

CREATE INDEX idx_user_id_rp ON room_player (user_id);

CREATE INDEX idx_is_ai ON room_player (is_ai);

CREATE INDEX idx_team_id ON room_player (team_id);

CREATE INDEX idx_is_ready ON room_player (is_ready);

CREATE INDEX idx_player_role ON room_player (player_role);

CREATE INDEX idx_position ON room_player (position);

CREATE INDEX idx_join_time ON room_player (join_time);

COMMENT ON TABLE room_player IS '房间玩家表';

COMMENT ON COLUMN room_player.room_id IS '房间ID(与game_room关联)';

COMMENT ON COLUMN room_player.user_id IS '用户ID,NULL为AI玩家';

COMMENT ON COLUMN room_player.is_ai IS '是否AI玩家:1-是,0-否';

COMMENT ON COLUMN room_player.ai_type IS 'AI类型:simple-简单,advanced-高级,custom-自定义';

COMMENT ON COLUMN room_player.ai_config IS 'AI配置参数(如难度、行为策略等)';

COMMENT ON COLUMN room_player.team_id IS '队伍ID:0-无队伍,1-队伍A,2-队伍B,依此类推';

COMMENT ON COLUMN room_player.is_ready IS '是否准备:1-已准备,0-未准备';

COMMENT ON COLUMN room_player.player_role IS '玩家角色:owner-房主,captain-队长,member-队员';

COMMENT ON COLUMN room_player.position IS '位置编号:用于记录玩家座位/顺序';

COMMENT ON COLUMN room_player.kill IS '击杀数';

COMMENT ON COLUMN room_player.death IS '死亡次数';

COMMENT ON COLUMN room_player.assist IS '协助数';

COMMENT ON COLUMN room_player.join_time IS '加入时间';

COMMENT ON COLUMN room_player.leave_time IS '离开时间';

-- 优化后的坦克大战表
DROP TABLE IF EXISTS tank_battle CASCADE;


CREATE TABLE tank_battle (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    room_id BIGINT

    map_name VARCHAR(50) DEFAULT 'classic',
    
    game_data JSONB,

    kills INT DEFAULT 0, -- 击杀数
    deaths INT DEFAULT 0, -- 死亡次数
    assists INT DEFAULT 0, -- 助攻数
    damage_dealt INT DEFAULT 0, -- 造成伤害
    damage_taken INT DEFAULT 0, -- 受到伤害

    score INT DEFAULT 0,
    final_rank INT,

-- 原有字段
duration INT DEFAULT 0,
    status SMALLINT DEFAULT 0, -- 0-进行中,1-已结束
    start_time TIMESTAMP,
    end_time TIMESTAMP,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- 索引优化
CREATE INDEX idx_user_id_tb ON tank_battle (user_id);

CREATE INDEX idx_room_id_tb ON tank_battle (room_id);

CREATE INDEX idx_map_name ON tank_battle (map_name);

CREATE INDEX idx_score_tb ON tank_battle (score DESC);
-- 排行榜查询
CREATE INDEX idx_user_score_tb ON tank_battle (user_id, score DESC);

CREATE INDEX idx_status_tb ON tank_battle (status);

CREATE INDEX idx_create_time_tb ON tank_battle (create_time DESC);

-- JSONB索引（提升查询性能）
CREATE INDEX idx_game_data_gin ON tank_battle USING GIN (game_data);

-- 注释
COMMENT ON COLUMN tank_battle.map_name IS '地图名称:classic-经典,desert-沙漠,forest-森林';

COMMENT ON COLUMN tank_battle.game_data IS '游戏数据(地图配置、障碍物、回放等)';

COMMENT ON COLUMN tank_battle.kills IS '击杀数';

COMMENT ON COLUMN tank_battle.deaths IS '死亡次数';

COMMENT ON COLUMN tank_battle.assists IS '助攻数';

COMMENT ON COLUMN tank_battle.score IS '得分(用于排行榜)';

-- ==================== 触发器函数 ====================

-- 1. 自动更新 updated_time 的触发器函数
CREATE OR REPLACE FUNCTION update_updated_time_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_time = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 为 user 表创建触发器
CREATE TRIGGER trigger_update_user_updated_time
    BEFORE UPDATE ON "user"
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_time_column();

-- 2. 2048游戏完成后更新用户积分的触发器函数（INSERT）
CREATE OR REPLACE FUNCTION update_user_score_after_2048_insert()
RETURNS TRIGGER AS $$
DECLARE
    base_score INT;
    difficulty_multiplier DECIMAL(3,1);
    final_score INT;
BEGIN
    -- 当插入的记录状态为通关(1)或失败(2)时，更新用户积分
    IF NEW.status IN (1, 2) THEN
        -- 根据游戏结果设定基础分数计算规则
        IF NEW.status = 1 THEN
            -- 赢了：得分除以50
            base_score := NEW.score / 50;
        ELSE
            -- 输了：得分除以150
            base_score := NEW.score / 150;
        END IF;
        
        -- 根据难度设定倍率
        CASE NEW.difficulty
            WHEN 'easy' THEN difficulty_multiplier := 1.0;
            WHEN 'normal' THEN difficulty_multiplier := 1.5;
            WHEN 'hard' THEN difficulty_multiplier := 2.0;
            ELSE difficulty_multiplier := 1.0;
        END CASE;
        
        -- 计算最终积分（只保留整数部分）
        final_score := FLOOR(base_score * difficulty_multiplier);
        
        -- 更新用户总积分
        UPDATE "user" 
        SET total_score = total_score + final_score
        WHERE id = NEW.user_id;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建 INSERT 触发器
CREATE TRIGGER trigger_2048_insert_update_score
    AFTER INSERT ON game2048
    FOR EACH ROW
    EXECUTE FUNCTION update_user_score_after_2048_insert();

-- 3. 2048游戏更新时更新用户积分的触发器函数（UPDATE）
CREATE OR REPLACE FUNCTION update_user_score_after_2048_update()
RETURNS TRIGGER AS $$
DECLARE
    base_score INT;
    difficulty_multiplier DECIMAL(3,1);
    final_score INT;
BEGIN
    -- 当状态从进行中(0)变为通关(1)或失败(2)时，更新用户积分
    IF (OLD.status = 0 AND NEW.status IN (1, 2)) THEN
        -- 根据游戏结果设定基础分数计算规则
        IF NEW.status = 1 THEN
            -- 赢了：得分除以50
            base_score := NEW.score / 50;
        ELSE
            -- 输了：得分除以150
            base_score := NEW.score / 150;
        END IF;
        
        -- 根据难度设定倍率
        CASE NEW.difficulty
            WHEN 'easy' THEN difficulty_multiplier := 1.0;
            WHEN 'normal' THEN difficulty_multiplier := 1.5;
            WHEN 'hard' THEN difficulty_multiplier := 2.0;
            ELSE difficulty_multiplier := 1.0;
        END CASE;
        
        -- 计算最终积分（只保留整数部分）
        final_score := FLOOR(base_score * difficulty_multiplier);
        
        -- 更新用户总积分
        UPDATE "user" 
        SET total_score = total_score + final_score
        WHERE id = NEW.user_id;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建 UPDATE 触发器
CREATE TRIGGER trigger_2048_update_update_score
    AFTER UPDATE ON game2048
    FOR EACH ROW
    EXECUTE FUNCTION update_user_score_after_2048_update();

-- 4. 根据积分更新用户等级的触发器函数
CREATE OR REPLACE FUNCTION update_user_level_on_score_change()
RETURNS TRIGGER AS $$
DECLARE
    level INT := 1;
    next_level_score INT;
BEGIN
    -- 只有当total_score发生变化时才更新等级
    IF NEW.total_score != OLD.total_score THEN
        -- 使用提供的算法计算当前等级
        LOOP
            -- 计算达到下一级所需的总积分
            -- 公式：level * (100 + 50 * (level - 1))
            next_level_score := level * (100 + 50 * (level - 1));
            
            -- 如果用户积分不足以达到下一级，则退出循环
            IF NEW.total_score < next_level_score THEN
                EXIT;
            END IF;
            
            level := level + 1;
        END LOOP;
        
        -- 设置新的等级
        NEW.current_level := level;
    END IF;
    
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 创建等级更新触发器
CREATE TRIGGER trigger_update_user_level
    BEFORE UPDATE ON "user"
    FOR EACH ROW
    EXECUTE FUNCTION update_user_level_on_score_change();