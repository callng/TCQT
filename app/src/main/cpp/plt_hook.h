#pragma once

namespace tcqt {

// bionic 的 dl_iterate_phdr 是一次性遍历（不会在后续 dlopen 时主动回调），
// 因此安装时同时做两件事：
//   1. 把当前已加载库对 dlopen 的调用改接内部 hook——任何一次 dlopen 返回
//      后立即重扫，第一时间发现 libfekit.so；
//   2. 后台轮询线程周期性扫描作为兜底（覆盖未被改接的调用方）。
void install_fekit_fopen_hook();

}  // namespace tcqt
