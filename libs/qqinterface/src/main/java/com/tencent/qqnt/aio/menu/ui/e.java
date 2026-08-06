package com.tencent.qqnt.aio.menu.ui;

import com.tencent.mobileqq.aio.msg.AIOMsgItem;

/**
 * 2026 / 08 / 06 （9.3.35）
 * 这个抽象类是被混淆的，从 9.3.35 的第一个公开测试版本开始使用本类
 */
public abstract class e {

    private static volatile Boolean b;
    private AIOMsgItem a;

    public e(AIOMsgItem aIOMsgItem) {
        this.a = aIOMsgItem;
    }

    public abstract int b();

    public abstract int c();

    public AIOMsgItem d() {
        return this.a;
    }

    public abstract String g();

    public abstract String h();

    public abstract void k();
}
