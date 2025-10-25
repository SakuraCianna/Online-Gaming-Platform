package com.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.entity.FriendChat;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.*;

public interface FriendChatMapper extends BaseMapper<FriendChat> {
    @Select("SELECT * from friend_chat " +
            "where (sender_id = #{userId} and " +
            "receiver_id = #{friendId}) or (sender_id = #{friendId} and receiver_id = #{userId}) " +
            "order by created_at")
    List<FriendChat> getFriendChatHistory(@Param("userId") long userId,@Param("friendId") long friendId);

    @Select("SELECT sender_id AS friendId, COUNT(*) AS unread " +
            "FROM friend_chat " +
            "WHERE receiver_id = #{userId} AND is_read = 0 " +
            "GROUP BY sender_id")
    List<Map<String, Object>> getUnreadFriendChat(long userId);

    @Update("UPDATE friend_chat " +
            "SET is_read = 1 " +
            "WHERE receiver_id = #{userId} AND sender_id = #{friendId} AND is_read = 0")
    void markFriendChatRead(Long userId, Long friendId);
}
