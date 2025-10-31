package com.game.controller;

import com.game.service.TankBattleService;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tankbattle")
public class TankBattleController {
    private final TankBattleService tankBattleService;

    public TankBattleController(TankBattleService tankBattleService) {
        this.tankBattleService = tankBattleService;
    }


}
