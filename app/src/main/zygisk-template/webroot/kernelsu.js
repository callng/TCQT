let callbackSeq = 0;

// Keep system bars visible; the manager supplies their insets as CSS variables.
export function enableEdgeToEdge() {
    try {
        if (typeof window.ksu?.enableEdgeToEdge !== "function") return false;
        window.ksu.enableEdgeToEdge(true);
        return true;
    } catch (_) { return false; }
}

/**
 * 在 root 环境执行一条 shell 命令。
 * @param {string} command
 * @param {object} [options]
 * @returns {Promise<{errno:number, stdout:string, stderr:string}>}
 */
export function exec(command, options = {}) {
    return new Promise((resolve, reject) => {
        const callbackName = `ksu_cb_${Date.now()}_${++callbackSeq}`;
        const timer = setTimeout(() => {
            delete window[callbackName];
            reject(new Error("管理器执行超时，请重新打开 WebUI 后重试"));
        }, 120000);
        window[callbackName] = (errno, stdout, stderr) => {
            clearTimeout(timer);
            try {
                delete window[callbackName];
            } catch (_) {
                window[callbackName] = undefined;
            }
            resolve({ errno, stdout, stderr });
        };
        try {
            window.ksu.exec(command, JSON.stringify(options), callbackName);
        } catch (error) {
            clearTimeout(timer);
            try {
                delete window[callbackName];
            } catch (_) {
                window[callbackName] = undefined;
            }
            reject(error);
        }
    });
}

/** 调用管理器的轻提示（不支持时静默忽略）。 */
export function toast(message) {
    try {
        window.ksu.toast(message);
    } catch (_) {
        // 静默忽略
    }
}
