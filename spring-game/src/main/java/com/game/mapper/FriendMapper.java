package com.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.game.entity.Friend;
import com.game.vo.FriendVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.*;

import java.util.List;

public interface FriendMapper extends BaseMapper<Friend> {
        @Select("SELECT u.id AS id, u.username AS name, u.avatar AS avatar, u.current_level AS level, f.status AS status, f.message AS message "
                        +
                        "FROM friend f JOIN \"user\" u ON u.id = f.friend_id " +
                        "WHERE f.user_id = #{userId} " +
                        "ORDER BY f.sort_rank , f.created_time DESC")
        List<FriendVO> getAllFriendVO(Page<FriendVO> page, @Param("userId") Long userId);

        @Delete("DELETE FROM friend WHERE (user_id = #{userId} AND friend_id = #{friendId}) " +
                        "OR (user_id = #{friendId} AND friend_id = #{userId})")
        void deleteFriend(@Param("userId") long userId, @Param("friendId") long friendId);

        @Select("SELECT u.id AS id,u.username AS name,u.avatar AS avatar,u.current_level AS level, f.status AS status, f.message AS message "
                        +
                        "FROM friend f JOIN \"user\" u ON u.id=f.friend_id " +
                        "WHERE f.user_id=#{userId} AND (u.username LIKE CONCAT('%',#{keyword},'%') " +
                        "OR u.id LIKE CONCAT('%',#{keyword},'%')) " +
                        "ORDER BY f.sort_rank,f.created_time DESC")
        List<FriendVO> selectFriendVOPage(Page<FriendVO> mpPage,
                        @Param("userId") Long userId,
                        @Param("keyword") String keyword);

        @Select("SELECT EXISTS(Select 1 from friend where user_id = #{userId} and friend_id = #{friendId} and status = 1)")
        boolean isFriend(@Param("userId") long userId, @Param("friendId") long friendId);

        @Insert("INSERT into friend (user_id, friend_id, message, status) VALUES (#{userId},#{friendId},#{message},0)")
        int addFriend(@Param("userId") long userId, @Param("friendId") long friendId, @Param("message") String message);

        @Select("SELECT EXISTS(SELECT 1 from friend where user_id = #{userId} and friend_id = #{friendId} and status = #{status})")
        boolean existFriendData(@Param("userId") long userId, @Param("friendId") long friendId,
                        @Param("status") int status);

        @Update("UPDATE friend SET status = #{status} WHERE user_id = #{userId} AND friend_id = #{friendId}")
        int updateStatus(@Param("userId") long userId, @Param("friendId") long friendId, @Param("status") int status);

        @Insert("INSERT into friend (user_id, friend_id, status) " +
                        "VALUES (#{userId}, #{friendId}, #{status})")
        void confirmFriend(@Param("userId") long userId, @Param("friendId") long friendId, @Param("status") int status);

        @Select("SELECT COUNT(*) FROM friend WHERE user_id = #{userId}")
        long countAllFriend(@Param("userId") Long userId);
}
