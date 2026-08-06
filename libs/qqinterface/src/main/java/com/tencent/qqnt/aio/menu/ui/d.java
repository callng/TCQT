package com.tencent.qqnt.aio.menu.ui;

import com.tencent.mobileqq.aio.msg.AIOMsgItem;

/**
 * 2025 / 08 / 28 （9.2.5）
 * 这个抽象类是被混淆的，但看了几个历史版本到目前最新版本都是相同的，先这样吧。
 */
public abstract class d {

    private AIOMsgItem c;

    public d(AIOMsgItem aIOMsgItem) {
        this.c = aIOMsgItem;
    }

    public abstract int b();

    public abstract int c();

    public AIOMsgItem d() {
        return this.c;
    }

    public abstract String e();

    public abstract String f();

    public abstract void h();
}
