package com.tencent.qqnt.aio.menu.ui;

import com.tencent.mobileqq.aio.msg.AIOMsgItem;

/**
 * 2025 / 11 / 04 （9.2.30）
 * 这个抽象类是被混淆的，从 9.2.30 的第一个公开测试版本开始使用本类
 */
public abstract class f {

    private AIOMsgItem a;

    public f(AIOMsgItem aIOMsgItem) {
        this.a = aIOMsgItem;
    }

    public abstract int b();

    public abstract int c();

    public AIOMsgItem d() {
        return this.a;
    }

    public abstract String e();

    public abstract String f();

    public abstract void h();
}
