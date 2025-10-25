package com.game.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.game.entity.User;
import com.game.vo.UserVO;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface UserMapper extends BaseMapper<User> {
        // 根据邮箱查询用户
        @Select("SELECT * FROM user WHERE email = #{email} LIMIT 1")
        User selectByEmail(@Param("email") String email);

        // 查找所有正常状态的用户
        @Select("SELECT * FROM user WHERE status = #{status}")
        List<User> selectByStatus(@Param("status") String status);

        // 根据用户名和状态查询用户
        @Select("SELECT * FROM user WHERE username = #{username} AND status = #{status}")
        User selectByUsernameAndStatus(@Param("username") String username, @Param("status") Integer status);

        // 在用户注册时插入验证码信息
        @Insert("INSERT INTO user (username, email, verification_code, verification_expire_time, email_verified) " +
                        "VALUES (#{params.username}, #{params.email}, #{params.verification_code}, " +
                        "#{params.verification_expire_time}, #{params.email_verified})")
        int insertUserCode(@Param("params") Map<String, Object> params);

        @Update("UPDATE user set username = #{params.username}, password = #{params.password}, avatar = #{params.avatar}, "
                        +
                        "email_verified = #{params.email_verified} where id = #{params.id}")
        int registerUpdate(@Param("params") Map<String, Object> params);

        @Update("update user set last_login_time = #{last_login_time} where id = #{id}")
        void updateLogin(@Param("last_login_time") LocalDateTime last_login_time, @Param("id") long id);

        @Update("UPDATE user set verification_code = #{verification_code}," +
                        "verification_expire_time = #{verification_expire_time} where email = #{email}")
        int updateCode(@Param("verification_code") String verification_code,
                        @Param("verification_expire_time") LocalDateTime verification_expire_time,
                        @Param("email") String email);

        @Update("UPDATE user set password = #{password} where email = #{email}")
        int updatePassword(@Param("email") String email, @Param("password") String password);

        @Select("SELECT EXISTS(SELECT 1 FROM user WHERE username = #{username} AND email != #{email})")
        int findUsername(@Param("username") String username, @Param("email") String email);

        @Select("SELECT id,username,avatar,total_score,current_level,status,created_time,last_login_time " +
                        "from user where username = #{username}")
        UserVO searchByUsername(String username);

        // 查询所有用户ID（用于布隆过滤器初始化）
        @Select("SELECT id FROM user")
        List<Long> selectAllUserIds();
}
