package com.game.config;

import com.game.service.GameRecordService;
import com.game.service.GomokuService;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import jakarta.annotation.PostConstruct;

/**
 * 解决Service层循环依赖配置
 * GomokuService需要GameRecordService来失效缓存
 * 但GameRecordService依赖GomokuMapper（被GomokuService使用）
 */
@Configuration
public class ServiceCircularDependencyConfig {

    private final GomokuService gomokuService;
    private final GameRecordService gameRecordService;

    public ServiceCircularDependencyConfig(@Lazy GomokuService gomokuService,
            @Lazy GameRecordService gameRecordService) {
        this.gomokuService = gomokuService;
        this.gameRecordService = gameRecordService;
    }

    /**
     * 在所有Bean创建完成后注入GameRecordService到GomokuService
     */
    @PostConstruct
    public void init() {
        gomokuService.setGameRecordService(gameRecordService);
    }
}
