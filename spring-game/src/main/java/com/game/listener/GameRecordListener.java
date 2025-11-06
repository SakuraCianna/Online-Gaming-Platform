package com.game.listener;

import com.game.component.MapUtil;
import com.game.config.RabbitMQConfig;
import com.game.dto.GameRecordMessageDTO;
import com.game.entity.Game2048;
import com.game.entity.Minesweeper;
import com.game.mapper.Game2048Mapper;
import com.game.mapper.GomokuMapper;
import com.game.mapper.MinesweeperMapper;
import com.game.mapper.TankBattleMapper;
import com.game.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class GameRecordListener {
    private final Game2048Mapper game2048Mapper;
    private final GomokuMapper gomokuMapper;
    private final MinesweeperMapper minesweeperMapper;
    private final TankBattleMapper tankBattleMapper;
    private final UserService userService;
    private final RedisTemplate<String, String> redisTemplate;  // 【新增】用于幂等性检查

    public GameRecordListener(Game2048Mapper game2048Mapper, GomokuMapper gomokuMapper, MinesweeperMapper minesweeperMapper, TankBattleMapper tankBattleMapper, UserService userService, RedisTemplate<String, String> redisTemplate) {
        this.game2048Mapper = game2048Mapper;
        this.gomokuMapper = gomokuMapper;
        this.minesweeperMapper = minesweeperMapper;
        this.tankBattleMapper = tankBattleMapper;
        this.userService = userService;
        this.redisTemplate = redisTemplate;  // 【新增】注入Redis模板
    }

    @RabbitListener(queues = RabbitMQConfig.GAME_RECORD_QUEUE,concurrency = "3-10")
    public void handleGameRecordSave(GameRecordMessageDTO message) {
        log.info("收到{}游戏的待保存消息: userId={}, messageId={}", 
                message.getGameType(), message.getUserId(), message.getMessageId());
        
        try {
            // 使用Redis存储已处理的messageId,避免重复消费
            String redisKey = "game:message:processed:" + message.getMessageId();
            // 如果redis里面不存在这个key且无值,则设置key为redisKey,值为1且设置过期时间为10分钟,命令执行成功返回true
            Boolean isProcessed = redisTemplate.opsForValue().setIfAbsent(
                    redisKey, "1", 10, TimeUnit.MINUTES
            ); // 原子命令

            if (Boolean.FALSE.equals(isProcessed)) {
                log.warn("消息已处理过，跳过: messageId={}", message.getMessageId());
                return;
            }
            
            switch (message.getGameType()) {
                case "2048" -> save2048(message);
                case "gomoku" -> saveGomoku(message);
                case "minesweeper" -> saveMinesweeper(message);
                case "tank_battle" -> saveTankBattle(message);
                default -> log.warn("未知的游戏类型: {}", message.getGameType());
            }
            
            log.info("游戏保存完成: gameType={}, userId={}", 
                    message.getGameType(), message.getUserId());
        } catch (Exception e) {
            log.error("处理游戏保存消息失败: messageId={}, 错误: {}", message.getMessageId(), e.getMessage(), e);
            throw new RuntimeException("保存游戏记录失败: " + e.getMessage(), e);
        }
    }

    private void saveTankBattle(GameRecordMessageDTO message) {

    }

    public void saveMinesweeper(GameRecordMessageDTO message) {
        Map<String, Object> extraData = message.getGameExtraData();
        long userId = message.getUserId();
        int status = message.getStatus();

        // 从额外数据中提取字段
        String difficulty = MapUtil.getValue(extraData, "difficulty", String.class);
        Integer boardWidth = MapUtil.getValue(extraData, "board_width", Integer.class);
        Integer boardHeight = MapUtil.getValue(extraData, "board_height", Integer.class);
        Integer mineCount = MapUtil.getValue(extraData, "mine_count", Integer.class);
        Integer correctFlags = MapUtil.getValue(extraData, "correct_flags", Integer.class);

        // 参数校验
        if (difficulty == null || difficulty.trim().isEmpty()) {
            log.error("扫雷游戏难度参数缺失: userId={}", userId);
            throw new IllegalArgumentException("游戏难度不能为空");
        }

        if (status == 0) {
            // 进行中的游戏：查询是否已有进行中的记录
            Minesweeper inProgress = minesweeperMapper.selectInProgressByUserId(userId);
            
            if (inProgress != null) {
                // 已存在进行中的游戏 → 更新
                log.info("更新已有的扫雷进行中游戏: userId={}, gameId={}", userId, inProgress.getId());
                updateMinesweeper(inProgress.getId(), message, difficulty, boardWidth, boardHeight, mineCount, correctFlags);
            } else {
                // 不存在 → 创建新记录
                log.info("创建新的扫雷进行中游戏: userId={}", userId);
                createMinesweeper(message, difficulty, boardWidth, boardHeight, mineCount, correctFlags);
            }
        } else {
            // 游戏结束（status=1或2）：总是创建新记录，保留完整游戏历史
            log.info("创建新记录: userId={}, status={}", userId, status);
            createMinesweeper(message, difficulty, boardWidth, boardHeight, mineCount, correctFlags);
        }
    }

    /**
     * 创建新的扫雷游戏记录
     */
    private void createMinesweeper(GameRecordMessageDTO message, String difficulty,
                                  Integer boardWidth, Integer boardHeight,
                                  Integer mineCount, Integer correctFlags) {
        Minesweeper game = new Minesweeper();
        game.setUserId(message.getUserId());
        game.setDifficulty(difficulty);
        game.setBoardWidth(boardWidth);
        game.setBoardHeight(boardHeight);
        game.setMineCount(mineCount);
        game.setCorrectFlags(correctFlags != null ? correctFlags : 0);
        game.setDuration(message.getDuration());
        game.setStatus(message.getStatus());
        game.setGameData(message.getGameData());
        game.setCreateTime(LocalDateTime.now());

        int rows = minesweeperMapper.insert(game);
        if (rows != 1) {
            log.error("扫雷游戏插入失败: userId={}", message.getUserId());
            throw new RuntimeException("游戏记录插入失败");
        }
        
        log.info("扫雷游戏创建成功: userId={}, status={}", message.getUserId(), message.getStatus());
    }

    /**
     * 更新已有的扫雷游戏记录（仅用于 status=0 的进行中游戏）
     */
    private void updateMinesweeper(Long gameId, GameRecordMessageDTO message,
                                  String difficulty, Integer boardWidth,
                                  Integer boardHeight, Integer mineCount,
                                  Integer correctFlags) {
        Minesweeper game = new Minesweeper();
        game.setId(gameId);
        game.setDifficulty(difficulty);
        game.setBoardWidth(boardWidth);
        game.setBoardHeight(boardHeight);
        game.setMineCount(mineCount);
        game.setCorrectFlags(correctFlags != null ? correctFlags : 0);
        game.setDuration(message.getDuration());
        game.setStatus(message.getStatus());
        game.setGameData(message.getGameData());

        int rows = minesweeperMapper.updateById(game);
        if (rows != 1) {
            log.error("扫雷游戏更新失败: gameId={}", gameId);
            throw new RuntimeException("游戏记录更新失败");
        }
        
        log.info("扫雷游戏更新成功: gameId={}, userId={}", gameId, message.getUserId());
    }

    private void saveGomoku(GameRecordMessageDTO message) {
    }

    public void save2048(GameRecordMessageDTO message) {
        Map<String, Object> extraData = message.getGameExtraData();
        long userId = message.getUserId();
        int status = message.getStatus();

        // 从额外数据中提取字段
        String difficulty = MapUtil.getValue(extraData, "difficulty", String.class);
        Integer maxTile = MapUtil.getValue(extraData, "max_tile", Integer.class);
        Integer movesCount = MapUtil.getValue(extraData, "moves_count", Integer.class);
        Integer score = MapUtil.getValue(extraData, "score", Integer.class);

        // 参数校验
        if (difficulty == null || difficulty.trim().isEmpty()) {
            log.error("2048游戏难度参数缺失: userId={}", userId);
            throw new IllegalArgumentException("游戏难度不能为空");
        }

        if (status == 0) {
            // 进行中的游戏：查询是否已有进行中的记录
            Game2048 inProgress = game2048Mapper.selectInProgressByUserId(userId);
            
            if (inProgress != null) {
                // 已存在进行中的游戏 → 更新
                log.info("更新已有的2048进行中游戏: userId={}, gameId={}", userId, inProgress.getId());
                update2048(inProgress.getId(), message, difficulty, maxTile, movesCount, score);
            } else {
                // 不存在 → 创建新记录
                log.info("创建新的2048进行中游戏: userId={}", userId);
                create2048(message, difficulty, maxTile, movesCount, score);
            }
        } else {
            // 游戏结束（status=1或2）：总是创建新记录，保留完整游戏历史
            log.info("游戏已结束，创建新记录: userId={}, status={}", userId, status);
            create2048(message, difficulty, maxTile, movesCount, score);
            
            // 更新用户积分
            boolean isWin = (status == 1);
            userService.updateUserScore(userId, score, difficulty, isWin);
            log.info("用户积分更新完成: userId={}, score={}, isWin={}", userId, score, isWin);
        }
    }

    private void create2048(GameRecordMessageDTO message, String difficulty, 
                           Integer maxTile, Integer movesCount, Integer score) {
        Game2048 game = new Game2048();
        game.setUserId(message.getUserId());
        game.setDifficulty(difficulty);
        game.setMaxTile(maxTile);
        game.setMovesCount(movesCount);
        game.setDuration(message.getDuration());
        game.setStatus(message.getStatus());
        game.setGameData(message.getGameData());
        game.setScore(score);
        game.setCreateTime(LocalDateTime.now());

        int rows = game2048Mapper.insert(game);
        if (rows != 1) {
            log.error("2048游戏插入失败: userId={}", message.getUserId());
            throw new RuntimeException("游戏记录插入失败");
        }
        
        log.info("2048游戏创建成功: userId={}, status={}", message.getUserId(), message.getStatus());
    }

    private void update2048(Long gameId, GameRecordMessageDTO message,
                            String difficulty, Integer maxTile,
                            Integer movesCount, Integer score) {
        Game2048 game = new Game2048();
        game.setId(gameId);
        game.setDifficulty(difficulty);
        game.setMaxTile(maxTile);
        game.setMovesCount(movesCount);
        game.setDuration(message.getDuration());
        game.setStatus(message.getStatus());
        game.setGameData(message.getGameData());
        game.setScore(score);

        int rows = game2048Mapper.updateById(game);
        if (rows != 1) {
            log.error("2048游戏更新失败: gameId={}", gameId);
            throw new RuntimeException("游戏记录更新失败");
        }
        
        log.info("2048游戏更新成功: gameId={}, userId={}", gameId, message.getUserId());
    }
}
