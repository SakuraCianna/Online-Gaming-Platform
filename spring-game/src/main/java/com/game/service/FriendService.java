package com.game.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.exception.BusinessException;
import com.game.mapper.FriendMapper;
import com.game.vo.FriendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Transactional
public class FriendService {

    private final FriendMapper friendMapper;
    // 使用 StringRedisTemplate：读取用户状态,与 UserStateService 保持一致
    private final StringRedisTemplate stringRedisTemplate;
    private final SimpMessagingTemplate messagingTemplate;

    public FriendService(FriendMapper friendMapper, StringRedisTemplate stringRedisTemplate,
            SimpMessagingTemplate messagingTemplate) {
        this.friendMapper = friendMapper;
        this.stringRedisTemplate = stringRedisTemplate;
        this.messagingTemplate = messagingTemplate;
    }

    public Map<String, Object> getAllFriend(long id, int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        Page<FriendVO> mpPage = new Page<>(page, pageSize);

        try {
            // 1) 分页查询静态信息（用户名、头像、等级）
            List<FriendVO> records = friendMapper.getAllFriendVO(mpPage, id);
            mpPage.setRecords(records);
            long total = friendMapper.countAllFriend(id);
            mpPage.setTotal(total);

            // 2) Redis 补充动态信息（在线状态、当前游戏）
            for (FriendVO vo : records) {
                addFriendState(vo);
            }

            result.put("data", mpPage.getRecords());
            result.put("total", mpPage.getTotal());
            result.put("success", true);
        } catch (Exception e) {
            throw new BusinessException(500, "分页查询失败");
        }
        return result;
    }

    public Map<String, Object> searchFriend(long userId, String keyword, int page, int pageSize) {
        Map<String, Object> result = new HashMap<>();
        Page<FriendVO> mpPage = new Page<>(page, pageSize);

        try {
            List<FriendVO> records = friendMapper.selectFriendVOPage(mpPage, userId, keyword);
            mpPage.setRecords(records);

            // 统一返回结构
            Map<String, Object> data = new HashMap<>();
            data.put("list", mpPage.getRecords());
            data.put("total", mpPage.getTotal());

            result.put("data", data);
            result.put("success", true);
            result.put("code", 200);
        } catch (Exception e) {
            throw new BusinessException(500, "搜索失败");
        }
        return result;
    }

    public Map<String, Object> addFriend(long userId, long friendId, String message) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (friendMapper.existFriendData(friendId, userId, 0)) {
                result.put("success", false);
                result.put("message", "已发送过好友请求,请勿重复添加!");
                return result;
            }
            if (friendMapper.addFriend(friendId, userId, message) > 0) {
                result.put("success", true);
                result.put("message", "发送好友请求成功");
                // WebSocket通知
                Map<String, Object> notifyMsg = new HashMap<>();
                notifyMsg.put("userId", userId);
                notifyMsg.put("friendId", friendId);
                notifyMsg.put("message", message);
                messagingTemplate.convertAndSend("/topic/friendRequest/" + friendId, notifyMsg);
            } else {
                result.put("success", false);
                result.put("message", "添加好友失败");
            }
        } catch (Exception e) {
            throw new BusinessException(500, "添加好友失败,请联系管理员");
        }
        return result;
    }

    public Map<String, Object> confirmFriend(long userId, long friendId, int status) {
        Map<String, Object> result = new HashMap<>();
        // 确认好友请求
        try {
            if (status == 1) {
                if (friendMapper.isFriend(userId, friendId)) {
                    result.put("success", false);
                    result.put("message", "你们已经是好友了");
                    return result;
                }
                // 更新现有请求状态为已接受
                if (friendMapper.updateStatus(userId, friendId, status) > 0) {
                    friendMapper.confirmFriend(friendId, userId, 1); // 添加反向好友关系
                    result.put("success", true);
                    result.put("message", "已接受好友请求");
                    Map<String, Object> notifyMsg = new HashMap<>();
                    notifyMsg.put("userId", userId); // 谁同意了
                    notifyMsg.put("friendId", friendId); // 谁发起的
                    notifyMsg.put("action", "confirm");
                    messagingTemplate.convertAndSend("/topic/friendRequest/" + friendId, notifyMsg);
                } else {
                    throw new BusinessException(500, "数据库错误");
                }
            } else if (status == 2) { // 拒绝好友请求
                friendMapper.deleteFriend(userId, friendId);
                result.put("success", true);
                result.put("message", "已拒绝请求");
            }
        } catch (Exception e) {
            throw new BusinessException(500, "处理好友请求失败,请联系管理员");
        }
        return result;
    }

    private void addFriendState(FriendVO vo) {
        Long friendId = vo.getId();
        String key = "user:state:" + friendId;

        List<Object> vals = stringRedisTemplate.opsForHash()
                .multiGet(key, Arrays.asList("online", "currentGame"));

        String onlineStr = vals.get(0) == null ? null : vals.get(0).toString();
        String currentGame = vals.get(1) == null ? null : vals.get(1).toString();

        boolean isOnline = "1".equals(onlineStr);

        vo.setOnline(isOnline);
        vo.setCurrentGame((currentGame == null || currentGame.isBlank()) ? null : currentGame);
    }

}