package com.game.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.game.component.RedisKeyManager;
import com.game.config.AiModelConfig;
import com.game.config.ZhipuAiUtil;
import com.game.entity.GameRoom;
import com.game.entity.Gomoku;
import com.game.entity.RoomPlayer;
import com.game.exception.BusinessException;
import com.game.mapper.GameRoomMapper;
import com.game.mapper.GomokuMapper;
import com.game.mapper.RoomPlayerMapper;
import com.game.vo.PointVO;
import lombok.Setter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
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

@Service
@Transactional
public class GomokuService {
    private static final String GOMOKU_PROMPT = """
            你是一个专业的五子棋AI助手，擅长分析棋局并给出最优落子策略。
            【游戏规则】
            - 棋盘大小：17x17
            - 胜利条件：横、竖、斜任意方向连成5子即获胜
            - 棋子颜色：X代表你的棋子，O代表对手棋子，.代表空位
            【任务要求】
            1. 分析当前棋盘局势
            2. 评估进攻和防守的优先级
            3. 找出最佳落子位置
            4. 优先级顺序：
               - 第一优先：如果有五连机会，立即落子取胜
               - 第二优先：如果对手有四连，必须防守
               - 第三优先：寻找形成活四、冲四的机会
               - 第四优先：寻找形成活三的机会
               - 第五优先：占据战略要地（中心区域、双方棋子附近）
            【输出格式】
            必须严格按照以下JSON格式返回，不要添加任何解释文字：
            {"x": 列坐标, "y": 行坐标}
            【注意事项】
            - 只返回一个落子位置
            - 坐标必须是0-16之间的整数
            - 不能落在已有棋子的位置（X或O）
            - 返回的坐标必须是空位（.）
            - 优先考虑攻防结合的策略
            - 分析时要考虑未来2-3步的变化
            """;

    private final RedisTemplate<String, Object> redisTemplate;
    private final GomokuMapper gomokuMapper;
    private final GameRoomMapper gameRoomMapper;
    private final RoomPlayerMapper roomPlayerMapper;
    private final ObjectMapper objectMapper;
    private final RedissonClient redissonClient;
    private final ZhipuAiUtil zhipuAiUtil;
    private final SimpMessagingTemplate messagingTemplate;
    private final RedisKeyManager redisKeyManager;
    // Setter注入，避免循环依赖
    @Setter
    private GameRecordService gameRecordService; // 延迟注入，避免循环依赖

    public GomokuService(RedisTemplate<String, Object> redisTemplate, GomokuMapper gomokuMapper,
            GameRoomMapper gameRoomMapper, RoomPlayerMapper roomPlayerMapper, ObjectMapper objectMapper,
            RedissonClient redissonClient, ZhipuAiUtil zhipuAiUtil,
            SimpMessagingTemplate messagingTemplate, RedisKeyManager redisKeyManager) {
        this.redisTemplate = redisTemplate;
        this.gomokuMapper = gomokuMapper;
        this.gameRoomMapper = gameRoomMapper;
        this.roomPlayerMapper = roomPlayerMapper;
        this.objectMapper = objectMapper;
        this.redissonClient = redissonClient;
        this.zhipuAiUtil = zhipuAiUtil;
        this.messagingTemplate = messagingTemplate;
        this.redisKeyManager = redisKeyManager;
    }

    public Map<String, Object> startGame(String roomCode, Map<String, Object> request) {
        Map<String, Object> result = new HashMap<>();

        try {
            Long userId = Long.parseLong(request.get("userId").toString());
            Long player1Id = Long.parseLong(request.get("player1Id").toString());
            Long player2Id = Long.parseLong(request.get("player2Id").toString());
            Boolean isAIGame = (Boolean) request.getOrDefault("isAIGame", false);

            String roomKey = "room:code:" + roomCode;
            Object gameRoomObj = redisTemplate.opsForValue().get(roomKey);

            if (gameRoomObj == null) {
                throw new BusinessException(404, "房间不存在");
            }

            GameRoom gameRoom = objectMapper.convertValue(gameRoomObj, GameRoom.class);

            // 权限检查
            if (!gameRoom.getCreatorId().equals(userId)) {
                throw new BusinessException(403, "开始游戏发起人不是房主!");
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
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "gameStart");
                notification.put("roomCode", roomCode);
                notification.put("player1Id", player1Id);
                notification.put("player2Id", player2Id);
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
                // 验证游戏是否是AI对战（player2Id为0表示AI对战）
                throw new BusinessException(403, "当前不是AI对战模式");
            }

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

            // 通过WebSocket广播落子信息给房间内所有玩家
            Map<String, Object> moveNotification = new HashMap<>();
            moveNotification.put("type", "move");
            moveNotification.put("x", point.getX());
            moveNotification.put("y", point.getY());
            moveNotification.put("color", point.getColor());
            moveNotification.put("id", point.getId());

            messagingTemplate.convertAndSend("/topic/room/" + roomCode, moveNotification);

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

            // 失效玩家缓存（两个玩家都需要失效）
            if (gameRecordService != null) {
                if (gomoku.getPlayer1Id() != null) {
                    gameRecordService.invalidateCache(gomoku.getPlayer1Id(), "gomoku");
                }
                if (gomoku.getPlayer2Id() != null) {
                    gameRecordService.invalidateCache(gomoku.getPlayer2Id(), "gomoku");
                }
            }

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
            PointVO aiMove = getAIMoveFromZhiPuAI(board, aiColor, lastMove, result);

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

    private PointVO getAIMoveFromZhiPuAI(int[][] board, int aiColor, PointVO lastMove, Map<String, Object> result) {
        try {
            // 构建用户提示词
            StringBuilder userPrompt = new StringBuilder();
            userPrompt.append("【当前棋盘状态】\n");
            userPrompt.append(formatBoard(board, aiColor));
            userPrompt.append("\n");

            userPrompt.append("【你的颜色】");
            userPrompt.append(aiColor == 1 ? "黑棋(1)" : "白棋(2)");
            userPrompt.append("\n");

            userPrompt.append("【对手颜色】");
            userPrompt.append(aiColor == 1 ? "白棋(2)" : "黑棋(1)");
            userPrompt.append("\n\n");

            if (lastMove != null) {
                userPrompt.append("【对手最后一步】");
                userPrompt.append("位置: (").append(lastMove.getX())
                        .append(", ").append(lastMove.getY()).append(")");
                userPrompt.append("\n\n");
            } else {
                userPrompt.append("【提示】这是开局，棋盘为空\n\n");
            }

            userPrompt.append("请根据当前棋盘局势，给出你的最佳落子位置。");

            // 调用智谱AI
            String aiResponse = zhipuAiUtil.advancedChat(
                    List.of(
                            zhipuAiUtil.buildSystemMessage(GOMOKU_PROMPT),
                            zhipuAiUtil.buildUserMessage(userPrompt.toString())),
                    AiModelConfig.MODEL_GLM_4_5_AIRX, // 使用传入的模型名称
                    2048, // maxTokens
                    0.7f, // temperature
                    false // enableThinking
            );

            result.put("aiResponse", aiResponse);

            // 解析AI返回的JSON结果
            PointVO aiMove = parseAIResponse(aiResponse);

            // 设置AI落子的颜色和ID
            aiMove.setColor(aiColor == 1 ? "black" : "white");
            aiMove.setId(0L);

            return aiMove;
        } catch (Exception e) {
            throw new BusinessException(500, "获取AI落子失败: " + e.getMessage());
        }
    }

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

    private boolean isValidMove(int[][] board, int x, int y) {
        if (x < 0 || x >= 17 || y < 0 || y >= 17) {
            return false;
        }
        return board[y][x] == 0;
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

                    // 确定当前用户的角色和对手
                    Long opponentId;
                    if (userId.equals(record.getPlayer1Id())) {
                        opponentId = record.getPlayer2Id();
                    } else {
                        opponentId = record.getPlayer1Id();
                    }

                    // 设置对手名称（这里需要查询user表，简化处理，可以在前端查询或后续优化）
                    // 暂时使用ID标识

                    // 确定游戏状态：win/lose/draw
                    String status;
                    if (record.getIsDraw() == 1) {
                        status = "draw";
                    } else if (userId.equals(record.getWinnerId())) {
                        status = "win";
                    } else if (userId.equals(record.getLoserId())) {
                        status = "lose";
                    } else {
                        status = "unknown";
                    }

                    // 将状态信息存入额外字段（注意：需要确保Entity有这些字段或使用Map）
                    // 这里我们返回原始数据，让前端根据winnerId判断
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

}
