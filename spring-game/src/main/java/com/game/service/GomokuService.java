package com.game.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.component.RedisKeyManager;
import com.game.component.OllamaUtil;
import com.game.config.AiModelConfig;
import com.game.entity.GameRoom;
import com.game.entity.Gomoku;
import com.game.entity.RoomPlayer;
import com.game.event.GameEndEvent;
import com.game.exception.BusinessException;
import com.game.mapper.GameRoomMapper;
import com.game.mapper.GomokuMapper;
import com.game.mapper.RoomPlayerMapper;
import com.game.vo.PointVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class GomokuService {
    private static final String GOMOKU_PROMPT = """
            你是一个专业的五子棋AI大师，拥有超强的棋局分析能力。

            【游戏规则】
            - 棋盘大小：17x17（坐标从0到16）
            - 胜利条件：横、竖、斜任意方向连成5子即获胜
            - 棋子颜色：X代表你的棋子，O代表对手棋子，.代表空位

            【关键棋型识别】
            1. **五连**：XXXXX（立即获胜）
            2. **活四**：.XXXX.（下一步必胜）
            3. **冲四**：OXXXX.或.XXXXO（一端被堵）
            4. **活三**：.XXX..或..XXX.（可形成活四）
            5. **眠三**：OXXX..或.XXXO（一端被堵）

            【决策优先级】（必须严格执行）
            **第一优先：立即获胜**
               - 如果你有五连机会，立即落子取胜
               - 检查所有方向：横、竖、左斜、右斜

            **第二优先：防守对手四连**
               - 如果对手有活四（.OOOO.），你下一步不防守就会输！
               - 如果对手有冲四（OOOOO.或.OOOOO），必须堵住缺口！
               - 这是生死关头，绝对不能忽视！

            **第三优先：创造活四**
               - 寻找能形成.XXXX.的位置
               - 活四是必胜棋型

            **第四优先：形成活三**
               - 寻找能形成.XXX..的位置
               - 为下一步的活四做准备

            **第五优先：战略要地**
               - 中心区域（8,8附近）
               - 对手棋子附近（2格以内）
               - 避免边角

            【分析步骤】
            1. 扫描全棋盘，标记所有X和O的连子
            2. 检查是否有五连机会→有则立即下
            3. 检查对手是否有四连→有则立即防守
            4. 寻找形成活四的机会
            5. 寻找形成活三的机会
            6. 选择战略位置

            【输出格式】
            必须严格按照JSON格式返回，不要任何解释：
            {"x": 列坐标, "y": 行坐标}

            【注意事项】
            - 坐标范围：0-16
            - 必须落在空位（.）
            - 防守四连比进攻更重要（防守失败=直接输）
            - 仔细检查每个方向（横竖斜）
            - 不要下在孤立位置
            """;

    private final RedisTemplate<String, Object> redisTemplate;
    private final GomokuMapper gomokuMapper;
    private final GameRoomMapper gameRoomMapper;
    private final RoomPlayerMapper roomPlayerMapper;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;
    private final OllamaUtil ollamaUtil;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisKeyManager redisKeyManager;
    private final ApplicationEventPublisher eventPublisher;

    public Map<String, Object> startGame(String roomCode, Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long userId = Long.parseLong(request.get("userId").toString());
            Long player1Id = Long.parseLong(request.get("player1Id").toString());
            Long player2Id = Long.parseLong(request.get("player2Id").toString());
            Boolean isAIGame = (Boolean) request.getOrDefault("isAIGame", false);

            String roomKey = redisKeyManager.buildRoomKey(roomCode);
            Object gameRoomObj = redisTemplate.opsForValue().get(roomKey);

            if (gameRoomObj == null) {
                throw new BusinessException(404, "房间不存在");
            }

            GameRoom gameRoom = objectMapper.convertValue(gameRoomObj, GameRoom.class);

            // 权限检查
            if (!gameRoom.getCreatorId().equals(userId)) {
                throw new BusinessException(403, "开始游戏发起人不是房主!");
            }
            if (gameRoom.getStatus() != 0) {
                throw new BusinessException(400, "游戏已开始");
            }
            // 修改GameRoom状态
            gameRoom.setStartTime(LocalDateTime.now());
            gameRoom.setStatus(1);
            gameRoom.setPlayers(2);

            // 保存回Redis
            redisTemplate.opsForValue().set(roomKey, gameRoom);

            Gomoku gomoku = new Gomoku();
            gomoku.setPlayer1Id(player1Id);
            gomoku.setPlayer2Id(player2Id);
            gomoku.setStartTime(LocalDateTime.now());
            gomoku.setGameData(new ArrayList<>());

            String gomokuKey = "gomoku:" + roomCode;
            redisTemplate.opsForValue().set(gomokuKey, gomoku);

            // 设置游戏信息初始 TTL 为 1 小时，同时刷新房间和玩家信息的 TTL
            redisKeyManager.setGameInitialTTL(roomCode, "gomoku");
            redisKeyManager.setRoomInitialTTL(roomCode);
            redisKeyManager.setPlayerInitialTTL(roomCode);

            // WebSocket通知（非AI游戏）
            if (!isAIGame) {
                // 判断房主是否是黑棋（player1是黑棋）
                boolean isOwnerBlack = gameRoom.getCreatorId().equals(player1Id);

                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "gameStart");
                notification.put("roomCode", roomCode);
                notification.put("player1Id", player1Id);
                notification.put("player2Id", player2Id);
                notification.put("isBlackFirst", isOwnerBlack); // 添加房主颜色信息
                notification.put("timestamp", System.currentTimeMillis());

                messagingTemplate.convertAndSend("/topic/room/" + roomCode, notification);
            }

            result.put("success", true);
            result.put("message", "游戏开始成功");
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "开始游戏失败: " + e.getMessage());
        }

        return result;
    }

    public Map<String, Object> makeMove(String roomCode, PointVO point) {
        Map<String, Object> result = new HashMap<>();
        String lockKey = "lock:gomoku:" + roomCode;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 获取锁
            boolean locked = lock.tryLock(1, 3, TimeUnit.SECONDS);
            if (!locked) {
                throw new BusinessException(409, "操作过快，请稍后再试");
            }

            String gameKey = "gomoku:" + roomCode;
            Boolean gameExists = redisTemplate.hasKey(gameKey);
            if (Boolean.FALSE.equals(gameExists) || gameExists == null) {
                throw new BusinessException(404, "游戏不存在");
            }

            Object gomokuObj = redisTemplate.opsForValue().get(gameKey);
            if (gomokuObj == null) {
                throw new BusinessException(404, "游戏状态异常");
            }
            Gomoku gomoku = objectMapper.convertValue(gomokuObj, Gomoku.class);

            // 参数校验
            if (point == null) {
                throw new BusinessException(400, "落子信息不能为空");
            }
            if (point.getX() < 0 || point.getX() >= 17 || point.getY() < 0 || point.getY() >= 17) {
                throw new BusinessException(400, "落子坐标超出棋盘范围");
            }
            if (point.getColor() == null || point.getColor().trim().isEmpty()) {
                throw new BusinessException(400, "棋子颜色不能为空");
            }

            // 验证玩家是否在游戏中（id是基本类型long，不需要null检查）
            long playerId = point.getId();
            Long player1Id = gomoku.getPlayer1Id();
            Long player2Id = gomoku.getPlayer2Id();

            if (playerId != 0 && !Long.valueOf(playerId).equals(player1Id)
                    && !Long.valueOf(playerId).equals(player2Id)) {
                throw new BusinessException(403, "你不在该游戏房间中");
            }

            // AI落子验证（id=0表示AI）
            if (playerId == 0 && !Long.valueOf(0L).equals(player2Id)) {
                throw new BusinessException(403, "当前不是AI对战模式");
            }

            // 判断是否是AI对战（双方都不是AI才是真人对战）
            boolean isAIGame = Long.valueOf(0L).equals(player2Id);

            // 获取棋谱列表
            List<PointVO> gameData = gomoku.getGameData();
            if (gameData == null) {
                gameData = new ArrayList<>();
            }

            for (PointVO existingPoint : gameData) {
                if (existingPoint.getX() == point.getX() &&
                        existingPoint.getY() == point.getY()) {
                    throw new BusinessException(400, "该位置已有棋子");
                }
            }

            if (!gameData.isEmpty()) {
                PointVO lastPoint = gameData.getLast();
                if (lastPoint.getColor().equals(point.getColor())) {
                    throw new BusinessException(400, "还没轮到你落子");
                }
            }

            // 通过所有检查后才添加
            gameData.add(point);
            gomoku.setGameData(gameData);

            // 更新步数
            gomoku.setMoveCount(gameData.size());

            // 保存回Redis
            redisTemplate.opsForValue().set(gameKey, gomoku);

            // 刷新所有相关 Key 的 TTL（包括游戏数据）
            redisKeyManager.refreshRoomTTL(roomCode, "gomoku");

            // 只在真人对战时通过WebSocket广播
            if (!isAIGame) {
                Map<String, Object> moveNotification = new HashMap<>();
                moveNotification.put("type", "move");
                moveNotification.put("x", point.getX());
                moveNotification.put("y", point.getY());
                moveNotification.put("color", point.getColor());
                moveNotification.put("id", point.getId());

                String topic = "/topic/room/" + roomCode;
                messagingTemplate.convertAndSend(topic, moveNotification);
            }

            result.put("success", true);
            result.put("message", "落子成功");

        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "操作被中断");
        } catch (Exception e) {
            throw new BusinessException(500, "落子失败: " + e.getMessage());
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }

        return result;
    }

    public Map<String, Object> endGame(String roomCode, Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        // 分布式锁，防止并发调用导致重复处理
        String lockKey = "lock:endGame:" + roomCode;
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // 优化锁时间：等待2秒，锁定5秒（endGame涉及数据库操作，需要更多时间）
            boolean locked = lock.tryLock(2, 5, TimeUnit.SECONDS);
            if (!locked) {
                // 获取锁失败，可能是另一个请求正在处理
                result.put("success", false);
                result.put("message", "系统繁忙，请稍后重试");
                return result;
            }

            // 从请求中提取数据
            Long winnerId = request.get("winnerId") != null ? Long.valueOf(request.get("winnerId").toString()) : null;
            Long loserId = request.get("loserId") != null ? Long.valueOf(request.get("loserId").toString()) : null;
            int isDraw = request.get("isDraw") != null ? Integer.parseInt(request.get("isDraw").toString()) : 0;

            if (isDraw == 0 && (winnerId == null || loserId == null)) {
                result.put("success", false);
                result.put("message", "游戏结束数据不完整");
                return result;
            }

            // 幂等性检查：如果房间数据不存在，说明游戏已经结束过了
            String roomKey = "room:code:" + roomCode;
            Boolean roomExists = redisTemplate.hasKey(roomKey);
            if (Boolean.FALSE.equals(roomExists) || roomExists == null) {
                result.put("success", true);
                result.put("message", "游戏已结束");
                return result;
            }

            // 获取GameRoom
            Object gameRoomObj = redisTemplate.opsForValue().get(roomKey);
            if (gameRoomObj == null) {
                result.put("success", true);
                result.put("message", "游戏已结束");
                return result;
            }
            GameRoom gameRoom = objectMapper.convertValue(gameRoomObj, GameRoom.class);

            // 获取Gomoku游戏数据
            String gomokuKey = "gomoku:" + roomCode;
            Object gomokuObj = redisTemplate.opsForValue().get(gomokuKey);
            if (gomokuObj == null) {
                // 与房间检查保持一致，游戏已结束
                result.put("success", true);
                result.put("message", "游戏已结束");
                return result;
            }
            Gomoku gomoku = objectMapper.convertValue(gomokuObj, Gomoku.class);

            // 获取房间玩家列表
            String playersKey = "room:players:" + roomCode;
            Map<Object, Object> playersMap = redisTemplate.opsForHash().entries(playersKey);
            List<RoomPlayer> roomPlayers = new ArrayList<>();
            for (Map.Entry<Object, Object> entry : playersMap.entrySet()) {
                RoomPlayer player = objectMapper.convertValue(entry.getValue(), RoomPlayer.class);
                roomPlayers.add(player);
            }

            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startTime = gomoku.getStartTime();
            int duration = (int) java.time.Duration.between(startTime, now).getSeconds();

            // 更新GameRoom
            gameRoom.setStatus(2);
            gameRoom.setWinnerId(winnerId);
            gameRoom.setEndTime(now);
            gameRoomMapper.insert(gameRoom);
            Long roomId = gameRoom.getId(); // 获取刚刚的房间的id

            // 更新Gomoku
            gomoku.setRoomId(roomId);
            gomoku.setWinnerId(winnerId);
            gomoku.setLoserId(loserId);
            gomoku.setIsDraw(isDraw);
            gomoku.setDuration(duration);
            gomoku.setEndTime(now);
            gomokuMapper.insert(gomoku);

            // 更新RoomPlayer（设置离开时间并保存到数据库）
            for (RoomPlayer player : roomPlayers) {
                player.setLeaveTime(now);
                player.setRoomId(roomId);
                roomPlayerMapper.insert(player);
            }

            // 发布游戏结束事件（异步处理缓存失效）
            eventPublisher.publishEvent(new GameEndEvent(
                    this,
                    gomoku.getPlayer1Id(),
                    "gomoku",
                    gomoku.getPlayer2Id()));

            // 删除Redis中的游戏数据
            redisTemplate.delete(roomKey);
            redisTemplate.delete(gomokuKey);
            redisTemplate.delete(playersKey);

            result.put("success", true);
            result.put("message", "游戏数据保存成功");
            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException(500, "操作被中断");
        } catch (Exception e) {
            throw new BusinessException(500, "保存游戏数据失败: " + e.getMessage());
        } finally {
            // 释放分布式锁
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 分页查询用户游戏记录
     *
     * @param userId 用户ID
     * @param page   页码（从1开始）
     * @param size   每页大小
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Map<String, Object> getRecordsByUserId(Long userId, Integer page, Integer size) {
        Map<String, Object> result = new HashMap<>();

        // 参数校验
        if (userId == null || userId <= 0) {
            throw new BusinessException(400, "用户ID不能为空");
        }
        if (page == null || page < 1) {
            page = 1;
        }
        if (size == null || size < 1 || size > 100) {
            size = 20; // 默认每页20条
        }

        try {
            // 计算偏移量
            int offset = (page - 1) * size;

            // 查询记录
            List<Gomoku> records = gomokuMapper.selectByUserIdWithPage(userId, offset, size);

            // 查询总数
            Integer total = gomokuMapper.countByUserId(userId);

            // 处理记录，添加对手名称和胜负状态
            if (records != null) {
                for (Gomoku record : records) {
                    // 清理敏感数据
                    record.setGameData(null);

                    // 返回原始数据，让前端根据winnerId和userId判断游戏结果
                }
            }

            result.put("success", true);
            result.put("records", records);
            result.put("total", total != null ? total : 0);
            result.put("page", page);
            result.put("size", size);
            result.put("totalPages", total != null ? (int) Math.ceil((double) total / size) : 0);
        } catch (Exception e) {
            throw new BusinessException(500, "查询游戏记录失败：" + e.getMessage());
        }

        return result;
    }

    private boolean isValidMove(int[][] board, int x, int y) {
        if (x < 0 || x >= 17 || y < 0 || y >= 17) {
            return false;
        }
        return board[y][x] == 0;
    }

    private static final int BOARD_SIZE = 17;
    private static final int WIN_COUNT = 5;
    private static final int MAX_DEPTH = 4;
    private static final int SEARCH_RADIUS = 2;

    /**
     * AI落子主方法（根据难度分配）
     */
    public Map<String, Integer> makeMoveByAI(int[][] board, int aiColor, int playerColor, String difficulty) {
        long startTime = System.currentTimeMillis();
        Map<String, Integer> move;

        try {
            // 根据难度选择不同的AI策略
            move = switch (difficulty.toLowerCase()) {
                case "easy" -> getEasyMove(board, aiColor, playerColor);
                case "medium" -> getMediumMove(board, aiColor, playerColor);
                case "hard" -> getHardMove(board, aiColor, playerColor);
                default -> {
                    log.warn("未知难度: {}, 使用中等难度", difficulty);
                    yield getMediumMove(board, aiColor, playerColor);
                }
            };

            long calculationTime = System.currentTimeMillis() - startTime;
            if (calculationTime > 5000) {
                log.warn("AI计算耗时过长: {}ms (难度: {})", calculationTime, difficulty);
            } else {
                log.info("AI计算耗时: {}ms (难度: {})", calculationTime, difficulty);
            }

            return move;
        } catch (Exception e) {
            long calculationTime = System.currentTimeMillis() - startTime;
            log.error("AI计算失败 (耗时: {}ms, 难度: {}): {}", calculationTime, difficulty, e.getMessage());
            // 降级到简单AI
            return getEasyMove(board, aiColor, playerColor);
        }
    }

    /**
     * 简单难度：完全随机落子（智力最低）
     */
    private Map<String, Integer> getEasyMove(int[][] board, int aiColor, int playerColor) {
        List<Map<String, Integer>> validMoves = new ArrayList<>();

        // 收集所有有效落子点
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (canPlaceStoneAI(board, r, c)) {
                    validMoves.add(Map.of("row", r, "col", c));
                }
            }
        }

        if (validMoves.isEmpty()) {
            return Map.of("row", 8, "col", 8);
        }

        // 完全随机，无任何策略
        int randomIndex = (int) (Math.random() * validMoves.size());
        return validMoves.get(randomIndex);
    }

    /**
     * 中等难度：评分系统 + 随机选择（降低智力）
     */
    private Map<String, Integer> getMediumMove(int[][] board, int aiColor, int playerColor) {
        // 不再检查必胜/必防，让AI有时会错过明显机会

        List<CandidatePoint> candidates = new ArrayList<>();
        boolean hasAnyStone = false;

        // 检查是否有棋子
        for (int i = 0; i < BOARD_SIZE && !hasAnyStone; i++) {
            for (int j = 0; j < BOARD_SIZE; j++) {
                if (board[i][j] != 0) {
                    hasAnyStone = true;
                    break;
                }
            }
        }

        // 收集候选位置并评分
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (!canPlaceStoneAI(board, r, c))
                    continue;

                // 如果是空棋盘，优先中心区域
                if (!hasAnyStone) {
                    int centerDist = Math.abs(r - 8) + Math.abs(c - 8);
                    if (centerDist <= 2) {
                        candidates.add(new CandidatePoint(r, c, 100 - centerDist));
                    }
                    continue;
                }

                if (isIsolatedPosition(board, r, c)) {
                    continue;
                }

                // 计算分数（降低防守权重）
                int attackScore = evaluatePositionAI(board, r, c, aiColor);
                int defenseScore = evaluatePositionAI(board, r, c, playerColor);
                int totalScore = attackScore + (int) (defenseScore * 0.8); // 降低防守意识

                candidates.add(new CandidatePoint(r, c, totalScore));
            }
        }

        if (candidates.isEmpty()) {
            // 棋盘已满或无有效位置，返回中心点
            return Map.of("row", 8, "col", 8);
        }

        // 排序候选位置
        candidates.sort((a, b) -> Integer.compare(b.totalScore, a.totalScore));

        // 从前5名中随机选择（引入随机性）
        int topN = Math.min(5, candidates.size());
        int randomIndex = (int) (Math.random() * topN);
        CandidatePoint selected = candidates.get(randomIndex);

        return Map.of("row", selected.row, "col", selected.col);
    }

    /**
     * 困难难度：MinMax算法 + Alpha-Beta剪枝
     */
    private Map<String, Integer> getHardMove(int[][] board, int aiColor, int playerColor) {
        // 1. 检查自己是否有一步致胜的棋
        Map<String, Integer> winMove = findWinningMove(board, aiColor);
        if (winMove != null)
            return winMove;

        // 2. 检查对方是否有一步致胜的棋（必须防守）
        Map<String, Integer> blockMove = findWinningMove(board, playerColor);
        if (blockMove != null)
            return blockMove;

        // 3. 检查自己是否有活四（下一步必胜）
        Map<String, Integer> myOpenFour = findOpenFourMove(board, aiColor);
        if (myOpenFour != null)
            return myOpenFour;

        // 4. 检查对方是否有活四（必须防守）
        Map<String, Integer> blockOpenFour = findOpenFourMove(board, playerColor);
        if (blockOpenFour != null)
            return blockOpenFour;

        // 5. 使用MinMax算法
        return findBestMoveWithMinMax(board, aiColor, playerColor);
    }

    /**
     * MinMax算法主入口
     */
    private Map<String, Integer> findBestMoveWithMinMax(int[][] board, int aiColor, int playerColor) {
        List<CandidatePoint> candidates = calculateAllCandidates(board, aiColor, playerColor);

        if (candidates.isEmpty()) {
            return Map.of("row", 8, "col", 8);
        }

        // 使用并行流提升性能
        CandidatePoint bestCandidate = candidates.parallelStream()
                .peek(candidate -> {
                    int[][] boardCopy = copyBoard(board);
                    boardCopy[candidate.row][candidate.col] = aiColor;
                    candidate.score = minMax(boardCopy, MAX_DEPTH - 1, Integer.MIN_VALUE,
                            Integer.MAX_VALUE, false, aiColor, playerColor, candidate.row, candidate.col);
                })
                .max((a, b) -> Integer.compare(a.score, b.score))
                .orElse(candidates.getFirst());

        return Map.of("row", bestCandidate.row, "col", bestCandidate.col);
    }

    /**
     * MinMax递归算法
     */
    private int minMax(int[][] board, int depth, int alpha, int beta,
            boolean isAI, int aiColor, int playerColor, int lastRow, int lastCol) {
        int currentColor = isAI ? aiColor : playerColor;

        // 检查游戏结束
        if (checkWinnerAI(board, lastRow, lastCol, currentColor)) {
            return currentColor == aiColor ? 10000000 : -10000000;
        }

        if (isBoardFullAI(board))
            return 0;

        // 达到搜索深度，评估棋盘
        if (depth == 0)
            return evaluateBoardAI(board, aiColor, playerColor);

        List<CandidatePoint> candidates = calculateAllCandidates(board, aiColor, playerColor);
        if (candidates.isEmpty())
            return 0;

        if (isAI) {
            // MAX层：AI落子
            int maxScore = Integer.MIN_VALUE;
            for (CandidatePoint candidate : candidates) {
                if (!canPlaceStoneAI(board, candidate.row, candidate.col))
                    continue;

                board[candidate.row][candidate.col] = aiColor;
                int childScore = minMax(board, depth - 1, alpha, beta, false, aiColor, playerColor,
                        candidate.row, candidate.col);
                board[candidate.row][candidate.col] = 0;

                maxScore = Math.max(maxScore, childScore);
                alpha = Math.max(alpha, childScore);
                if (beta <= alpha)
                    break;
            }
            return maxScore;
        } else {
            // MIN层：玩家落子
            int minScore = Integer.MAX_VALUE;
            for (CandidatePoint candidate : candidates) {
                if (!canPlaceStoneAI(board, candidate.row, candidate.col))
                    continue;

                board[candidate.row][candidate.col] = playerColor;
                int childScore = minMax(board, depth - 1, alpha, beta, true, aiColor, playerColor,
                        candidate.row, candidate.col);
                board[candidate.row][candidate.col] = 0;

                minScore = Math.min(minScore, childScore);
                beta = Math.min(beta, childScore);
                if (beta <= alpha)
                    break;
            }
            return minScore;
        }
    }

    /**
     * 计算所有候选位置并排序
     */
    private List<CandidatePoint> calculateAllCandidates(int[][] board, int aiColor, int playerColor) {
        List<CandidatePoint> candidates = new ArrayList<>();

        boolean hasAnyStone = false;
        for (int r = 0; r < BOARD_SIZE && !hasAnyStone; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] != 0) {
                    hasAnyStone = true;
                    break;
                }
            }
        }

        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] != 0)
                    continue;

                if (hasAnyStone && isIsolatedPosition(board, r, c)) {
                    continue;
                }

                int attackScore = evaluatePositionAI(board, r, c, aiColor);
                int defenseScore = evaluatePositionAI(board, r, c, playerColor);

                double defenseMultiplier = 1.2;
                if (defenseScore >= 100000) {
                    defenseMultiplier = 2.0;
                } else if (defenseScore >= 10000) {
                    defenseMultiplier = 1.5;
                }

                int totalScore = (int) (attackScore + defenseScore * defenseMultiplier);
                candidates.add(new CandidatePoint(r, c, totalScore));
            }
        }

        candidates.sort((a, b) -> Integer.compare(b.totalScore, a.totalScore));
        return candidates;
    }

    private int evaluatePositionAI(int[][] board, int row, int col, int color) {
        int score = 0;
        int[][] directions = { { 0, 1 }, { 1, 0 }, { 1, 1 }, { 1, -1 } };

        for (int[] dir : directions) {
            score += evaluateLineAI(board, row, col, dir[0], dir[1], color);
        }
        return score;
    }

    private int evaluateLineAI(int[][] board, int row, int col, int dr, int dc, int color) {
        int original = board[row][col];
        board[row][col] = color;

        LineCount lineCount = countLineStonesAI(board, row, col, dr, dc, color);
        int score = calculateLineScoreAI(lineCount.count, lineCount.openEnds);

        board[row][col] = original;
        return score;
    }

    private LineCount countLineStonesAI(int[][] board, int row, int col, int dr, int dc, int color) {
        int count = 1;
        int openEnds = 0;

        int r = row + dr, c = col + dc, tempCount = 0;
        while (isValidPositionAI(r, c)) {
            if (board[r][c] == color) {
                tempCount++;
                r += dr;
                c += dc;
            } else if (board[r][c] == 0) {
                openEnds++;
                break;
            } else {
                break;
            }
        }
        count += tempCount;

        r = row - dr;
        c = col - dc;
        tempCount = 0;
        while (isValidPositionAI(r, c)) {
            if (board[r][c] == color) {
                tempCount++;
                r -= dr;
                c -= dc;
            } else if (board[r][c] == 0) {
                openEnds++;
                break;
            } else {
                break;
            }
        }
        count += tempCount;

        return new LineCount(count, openEnds);
    }

    private int calculateLineScoreAI(int count, int openEnds) {
        if (count >= 5)
            return 10000000;

        int[][] scoreMap = {
                { 0, 0, 0 },
                { 1, 10, 100 },
                { 0, 1000, 10000 },
                { 0, 10000, 100000 },
                { 0, 400000, 2000000 }
        };

        if (openEnds < 3) {
            return scoreMap[count][openEnds];
        }
        return 0;
    }

    private int evaluateBoardAI(int[][] board, int aiColor, int playerColor) {
        int score = 0;
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                if (board[row][col] == aiColor) {
                    score += evaluatePositionAI(board, row, col, aiColor);
                } else if (board[row][col] == playerColor) {
                    score -= evaluatePositionAI(board, row, col, playerColor);
                }
            }
        }
        return score;
    }

    private Map<String, Integer> findWinningMove(int[][] board, int color) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] != 0)
                    continue;
                board[r][c] = color;
                boolean wins = checkWinnerAI(board, r, c, color);
                board[r][c] = 0;
                if (wins)
                    return Map.of("row", r, "col", c);
            }
        }
        return null;
    }

    private Map<String, Integer> findOpenFourMove(int[][] board, int color) {
        int[][] directions = { { 0, 1 }, { 1, 0 }, { 1, 1 }, { 1, -1 } };
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] != 0)
                    continue;

                for (int[] dir : directions) {
                    board[r][c] = color;
                    LineCount lineCount = countLineStonesAI(board, r, c, dir[0], dir[1], color);
                    board[r][c] = 0;

                    if (lineCount.count == 4 && lineCount.openEnds == 2) {
                        return Map.of("row", r, "col", c);
                    }
                }
            }
        }
        return null;
    }

    private boolean checkWinnerAI(int[][] board, int row, int col, int color) {
        int[][] directions = { { 0, 1 }, { 1, 0 }, { 1, 1 }, { 1, -1 } };
        for (int[] dir : directions) {
            LineCount lineCount = countLineStonesAI(board, row, col, dir[0], dir[1], color);
            if (lineCount.count >= WIN_COUNT)
                return true;
        }
        return false;
    }

    /**
     * 检查位置是否孤立（周围没有棋子）
     */
    private boolean isIsolatedPosition(int[][] board, int row, int col) {
        for (int r = row - SEARCH_RADIUS; r <= row + SEARCH_RADIUS; r++) {
            for (int c = col - SEARCH_RADIUS; c <= col + SEARCH_RADIUS; c++) {
                if (r == row && c == col)
                    continue;
                if (isValidPositionAI(r, c) && board[r][c] != 0)
                    return false; // 有棋子，不孤立
            }
        }
        return true; // 周围没棋子，孤立
    }

    private boolean canPlaceStoneAI(int[][] board, int row, int col) {
        return isValidPositionAI(row, col) && board[row][col] == 0;
    }

    private boolean isValidPositionAI(int row, int col) {
        return row >= 0 && row < BOARD_SIZE && col >= 0 && col < BOARD_SIZE;
    }

    private boolean isBoardFullAI(int[][] board) {
        for (int r = 0; r < BOARD_SIZE; r++) {
            for (int c = 0; c < BOARD_SIZE; c++) {
                if (board[r][c] == 0)
                    return false;
            }
        }
        return true;
    }

    private int[][] copyBoard(int[][] board) {
        int[][] copy = new int[BOARD_SIZE][BOARD_SIZE];
        for (int i = 0; i < BOARD_SIZE; i++) {
            System.arraycopy(board[i], 0, copy[i], 0, BOARD_SIZE);
        }
        return copy;
    }

    // 辅助类
    private static class CandidatePoint {
        int row, col, totalScore, score;

        CandidatePoint(int row, int col, int totalScore) {
            this.row = row;
            this.col = col;
            this.totalScore = totalScore;
        }
    }

    private static class LineCount {
        int count, openEnds;

        LineCount(int count, int openEnds) {
            this.count = count;
            this.openEnds = openEnds;
        }
    }

    @Deprecated
    public Map<String, Object> AI_Move(Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();
        try {
            // 处理board的类型转换
            int[][] board;
            Object boardObj = request.get("board");
            if (boardObj instanceof List) {
                @SuppressWarnings("unchecked")
                List<List<Integer>> boardList = (List<List<Integer>>) boardObj;
                board = new int[boardList.size()][];
                for (int i = 0; i < boardList.size(); i++) {
                    List<Integer> row = boardList.get(i);
                    board[i] = new int[row.size()];
                    for (int j = 0; j < row.size(); j++) {
                        board[i][j] = row.get(j);
                    }
                }
            } else {
                board = (int[][]) boardObj;
            }

            int aiColor = (int) request.get("aiColor");

            // lastMove可能为null
            PointVO lastMove = null;
            if (request.get("lastMove") != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> lastMoveMap = (Map<String, Object>) request.get("lastMove");
                lastMove = new PointVO();
                lastMove.setX((int) lastMoveMap.get("x"));
                lastMove.setY((int) lastMoveMap.get("y"));
                lastMove.setColor((String) lastMoveMap.get("color"));
                lastMove.setId(((Number) lastMoveMap.get("id")).longValue());
            }

            // 调用大模型获取AI落子
            PointVO aiMove = getAIMoveFromOllama(board, aiColor, lastMove, result);
            log.info("落子信息为:{}", aiMove);
            // 验证AI返回的落子位置是否有效
            if (!isValidMove(board, aiMove.getX(), aiMove.getY())) {
                throw new BusinessException(4001, "AI返回了无效的落子位置: (" + aiMove.getX() + ", " + aiMove.getY() + ")");
            }

            result.put("success", true);
            result.put("message", "计算下一步落子成功");
            result.put("move", aiMove);
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            throw new BusinessException(500, "计算失败: " + e.getMessage());
        }
        return result;
    }

    @Deprecated
    private PointVO getAIMoveFromOllama(int[][] board, int aiColor, PointVO lastMove, Map<String, Object> result) {
        try {
            // 构建用户提示词
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("【当前棋盘状态】\n");
            userPrompt.append(formatBoard(board, aiColor));
            userPrompt.append("\n");

            userPrompt.append("【你的颜色】");
            userPrompt.append(aiColor == 1 ? "黑棋(X)" : "白棋(O)");
            userPrompt.append("\n");

            userPrompt.append("【对手颜色】");
            userPrompt.append(aiColor == 1 ? "白棋(O)" : "黑棋(X)");
            userPrompt.append("\n\n");

            // 统计已占用位置
            int occupiedCount = 0;
            for (int y = 0; y < 17; y++) {
                for (int x = 0; x < 17; x++) {
                    if (board[y][x] != 0)
                        occupiedCount++;
                }
            }

            userPrompt.append("重要提示:\n");
            userPrompt.append("- 当前棋盘已有 ").append(occupiedCount).append(" 个棋子\n");
            userPrompt.append("- 你必须选择标记为'.'的空位（空位值为0）\n");
            userPrompt.append("- X和O的位置已被占用，绝对不能选择！\n");
            userPrompt.append("- 如果你返回已占用位置，你的落子将被判定为无效并导致游戏异常！\n\n");

            if (lastMove != null) {
                userPrompt.append("【对手最后一步】");
                userPrompt.append("位置: (").append(lastMove.getX())
                        .append(", ").append(lastMove.getY()).append(")");
                userPrompt.append("，该位置已被占用\n\n");
            } else {
                userPrompt.append("【提示】这是开局，棋盘为空\n\n");
            }

            userPrompt.append("现在请仔细观察棋盘，只在标记为'.'的位置中选择最佳落子点，直接返回JSON格式坐标：");

            // 调用Ollama
            String aiResponse = ollamaUtil.advancedChat(
                    GOMOKU_PROMPT,
                    userPrompt.toString(),
                    AiModelConfig.MODEL_QWEN_7B_INSTRUCT,
                    0.15);

            result.put("aiResponse", aiResponse);
            result.put("model", AiModelConfig.MODEL_QWEN_7B_INSTRUCT);
            result.put("aiEngine", "Ollama");

            // 解析AI返回的JSON结果
            PointVO aiMove = parseAIResponse(aiResponse);

            // 设置AI落子的颜色和ID
            aiMove.setColor(aiColor == 1 ? "black" : "white");
            aiMove.setId(0L);

            return aiMove;
        } catch (Exception e) {
            throw new BusinessException(500, "获取Ollama AI落子失败: " + e.getMessage());
        }
    }

    @Deprecated
    private String formatBoard(int[][] board, int aiColor) {
        char aiSymbol = (aiColor == 1) ? 'X' : 'O';
        char opponentSymbol = (aiColor == 1) ? 'O' : 'X';
        StringBuilder sb = new StringBuilder();

        sb.append("   ");
        for (int i = 0; i < 17; i++) {
            sb.append(String.format("%2d", i));
        }
        sb.append("\n");

        // 分隔线
        sb.append("   ");
        sb.append("--".repeat(17));
        sb.append("\n");

        // 棋盘内容
        for (int y = 0; y < 17; y++) {
            sb.append(String.format("%2d|", y));
            for (int x = 0; x < 17; x++) {
                char cellChar = switch (board[y][x]) {
                    case 1 -> (aiColor == 1) ? aiSymbol : opponentSymbol;
                    case 2 -> (aiColor == 2) ? aiSymbol : opponentSymbol;
                    default -> '.';
                };
                sb.append(String.format(" %c", cellChar));
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    @Deprecated
    private PointVO parseAIResponse(String aiResponse) {
        try {
            String cleanedResponse = aiResponse.trim();

            if (cleanedResponse.contains("```json")) {
                cleanedResponse = cleanedResponse.replace("```json", "").replace("```", "").trim();
            }

            Map<String, Object> responseMap = objectMapper.readValue(
                    cleanedResponse,
                    new TypeReference<>() {
                    });

            PointVO point = new PointVO();
            point.setX(((Number) responseMap.get("x")).intValue());
            point.setY(((Number) responseMap.get("y")).intValue());

            return point;

        } catch (Exception e) {
            throw new BusinessException(500, "解析AI响应失败: " + e.getMessage() + "，响应内容: " + aiResponse);
        }
    }
}
