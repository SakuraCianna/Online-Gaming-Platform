package com.game.config;

import com.game.mapper.UserMapper;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Redis布隆过滤器配置
 */
@Configuration
public class RedisBloomFilterConfig {

    private final RedissonClient redissonClient;
    private final UserMapper userMapper;

    public RedisBloomFilterConfig(RedissonClient redissonClient, UserMapper userMapper) {
        this.redissonClient = redissonClient;
        this.userMapper = userMapper;
    }

    /**
     * 创建用户ID布隆过滤器Bean
     * 预计容量：10000个用户
     * 误判率：0.01（1%）
     */
    @Bean
    public RBloomFilter<Long> userIdBloomFilter() {
        RBloomFilter<Long> bloomFilter = redissonClient.getBloomFilter("game:user:bloom:filter");
        // 初始化布隆过滤器（预期元素数量10000，误判率0.01）
        // tryInit返回true表示初始化成功，false表示已存在
        boolean initialized = bloomFilter.tryInit(10000L, 0.01);
        if (initialized) {
            System.out.println("布隆过滤器结构初始化成功");
        } else {
            System.out.println("布隆过滤器已存在，使用现有配置");
        }
        return bloomFilter;
    }

    /**
     * 应用启动时加载所有用户ID到布隆过滤器
     */
    @Bean
    public CommandLineRunner initBloomFilter(RBloomFilter<Long> userIdBloomFilter) {
        return args -> {
            try {
                // 检查布隆过滤器是否已有数据
                if (userIdBloomFilter.count() == 0) {
                    // 从数据库加载所有用户ID
                    List<Long> userIds = userMapper.selectAllUserIds();

                    if (userIds != null && !userIds.isEmpty()) {
                        // 批量添加到布隆过滤器
                        for (Long userId : userIds) {
                            userIdBloomFilter.add(userId);
                        }
                        System.out.println("布隆过滤器初始化完成，已加载 " + userIds.size() + " 个用户ID");
                    } else {
                        System.out.println("布隆过滤器初始化完成，当前无用户数据");
                    }
                } else {
                    System.out.println("布隆过滤器已存在数据，跳过初始化");
                }
            } catch (Exception e) {
                System.err.println("布隆过滤器初始化失败：" + e.getMessage());
                // 不阻止应用启动，只记录错误
            }
        };
    }
}
