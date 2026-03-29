package com.tencent.mobileqq.troop.robot.api;

import mqq.app.api.IRuntimeService;

public interface ITroopRobotService extends IRuntimeService {
    boolean isRobotUin(long j);

    boolean isRobotUin(String str);
}
