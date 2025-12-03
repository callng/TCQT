package com.owo233.tcqt.hooks

import android.content.Context
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.annotations.RegisterSetting
import com.owo233.tcqt.annotations.SettingType
import com.owo233.tcqt.ext.ActionProcess
import com.owo233.tcqt.ext.IAction
import com.owo233.tcqt.generated.GeneratedSettingList
import com.owo233.tcqt.utils.hookBeforeMethod
import com.tencent.smtt.sdk.WebView
import com.tencent.smtt.sdk.WebViewClient

@RegisterAction
@RegisterSetting(
    key = "inject_console",
    name = "注入Console",
    type = SettingType.BOOLEAN,
    desc = "对宿主内置浏览器注入Console，方便调试。此外，无论本功能是否启用，都会对每个网页注入剪切板保护代码。",
    uiOrder = 25
)
class InjectConsole : IAction {

    companion object {
        private var enableConsole = false
    }

    override fun onRun(ctx: Context, process: ActionProcess) {
        // 不需要任何代码
    }

    override fun canRun(): Boolean {
        enableConsole = GeneratedSettingList.getBoolean(key)
        installHookIfNeeded()
        return enableConsole
    }

    private fun installHookIfNeeded() {
        WebViewClient::class.java.hookBeforeMethod(
            "onPageFinished",
            WebView::class.java,
            String::class.java
        ) { param ->
            val webView = param.args[0] as WebView
            val url = param.args[1] as String

            // 无论如何都要注入 copy-block
            if (url != "about:blank") {
                blockTextCopy(webView)
                // 只有功能开启时才注入 console
                if (enableConsole) {
                    loadJavaScriptByEruda(webView)
                }
            }
        }
    }

    private fun loadJavaScriptByEruda(webView: WebView) {
        val jsCode = """
            (() => {
                if (window.eruda) {
                    console.log('[Eruda] 已存在，跳过加载');
                    return;
                }

                const script = document.createElement('script');
                script.src = 'https://cdn.jsdelivr.net/npm/eruda';
                script.async = true;

                script.onload = () => {
                    try {
                        eruda.init();
                        console.log('[Eruda] 初始化成功');
                    } catch (error) {
                        console.error('[Eruda] 初始化失败', error);
                    }
                };

                script.onerror = () => {
                    console.error('[Eruda] 加载失败: 资源加载错误');
                };

                (document.head || document.body).appendChild(script);
            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    private fun blockTextCopy(webView: WebView) {
        val jsCode = $$"""
            (function() {
            	if (window._copyInterceptorInstalled) return;
            	window._copyInterceptorInstalled = true;

            	// --- 配置常量 ---
            	const PREF_KEY = 'copy_intercept_pref_';
            	const COOLDOWN_MS = 1000;

            	// --- 全局状态变量 ---
            	var _isSelfCopy = false; // 锁
            	var _lastCopyTime = 0; // 防刷

            	function safeJsonParse(jsonString) {
            		try {
            			return JSON.parse(jsonString);
            		} catch (e) {
            			return null;
            		}
            	}

            	function getDomainKey() {
            		try {
            			// 使用 location.hostname 作为域名标识
            			return PREF_KEY + location.hostname;
            		} catch (e) {
            			return PREF_KEY + 'unknown';
            		}
            	}

            	function checkPreference() {
            		if (window.TCQTBrowser && window.TCQTBrowser.getSetting) {
            			try {
            				const jsonResult = window.TCQTBrowser.getSetting(getDomainKey());
            				const data = safeJsonParse(jsonResult);
            				// 返回保存的偏好: 'allow', 'deny', 或 empty (未设置)
            				if (data && data.value) {
            					return data.value;
            				}
            			} catch (e) {
            				console.error('TCQTBrowser.getSetting 调用失败', e);
            			}
            		}
            		return null;
            	}

            	function savePreference(pref) {
            		if (window.TCQTBrowser && window.TCQTBrowser.saveValueS) {
            			try {
            				window.TCQTBrowser.saveValueS(getDomainKey(), pref);
            				console.log('复制偏好设置已保存到 TCQTSetting：', getDomainKey(), pref);
            			} catch (e) {
            				console.error('TCQTBrowser.saveValueS 调用失败', e);
            			}
            		}
            	}

            	// --- 弹窗 UI 逻辑 ---
            	function showCopyConfirm(text) {
            		return new Promise(resolve => {
            			const displayText = text.length > 50 ? text.slice(0, 47) + '...' : text;

            			const overlay = document.createElement('div');
            			overlay.id = 'copy-interceptor-overlay';
            			overlay.style.cssText = `
                                                    position: fixed; top: 0; left: 0; width: 100%; height: 100%;
                                                    background: rgba(0,0,0,0.6); z-index: 2147483647;
                                                    display: flex; justify-content: center; align-items: center;
                                                    font-family: sans-serif; user-select: none;
                                                    -webkit-user-select: none; touch-action: none;
                                                `;

            			const dialog = document.createElement('div');
            			dialog.id = 'copy-interceptor-dialog';
            			dialog.style.cssText = `
                                                    background: white; width: 85%; max-width: 320px;
                                                    border-radius: 12px; padding: 20px;
                                                    box-shadow: 0 4px 20px rgba(0,0,0,0.2);
                                                    display: flex; flex-direction: column; gap: 15px;
                                                    pointer-events: auto;
                                                `;

            			dialog.innerHTML = `
                                                    <div style="font-weight:bold; font-size:18px; color:#333;">复制请求</div>
                                                    <div style="font-size:14px; color:#666;">网站试图写入剪切板：</div>
                                                    <div style="
                                                        background: #f1f1f1; padding: 10px; border-radius: 6px;
                                                        color: #333; font-size: 13px; max-height: 100px;
                                                        overflow-y: auto; word-break: break-all; border: 1px solid #ddd;
                                                    ">${displayText.replace(/</g,'&lt;')}</div>

                                                    <div style="display: flex; align-items: center; font-size: 13px; color: #555;">
                                                        <input type="checkbox" id="copy-no-ask" style="margin-right: 6px;"/>
                                                        <label for="copy-no-ask">该域名下不再询问</label>
                                                    </div>

                                                    <div style="display: flex; gap: 10px; margin-top: 5px;">
                                                        <button id="btn-deny" style="
                                                            flex: 1; padding: 10px; border: none; border-radius: 6px;
                                                            background: #f5f5f5; color: #666; font-weight: bold;
                                                        ">拒绝</button>
                                                        <button id="btn-allow" style="
                                                            flex: 1; padding: 10px; border: none; border-radius: 6px;
                                                            background: #2196F3; color: white; font-weight: bold;
                                                        ">允许</button>
                                                    </div>
                                                `;

            			overlay.appendChild(dialog);
            			document.body.appendChild(overlay);

            			const noAskCheckbox = dialog.querySelector('#copy-no-ask');

            			const cleanup = () => {
            				if (document.body.contains(overlay)) {
            					document.body.removeChild(overlay);
            				}
            			};

            			overlay.onclick = (e) => {
            				if (e.target.id === 'copy-interceptor-overlay') {
            					e.stopPropagation();
            					cleanup();
            					resolve(false);
            				}
            			};
            			dialog.onclick = (e) => {
            				e.stopPropagation();
            			};

            			// 拒绝
            			dialog.querySelector('#btn-deny').onclick = (e) => {
            				e.stopPropagation();
            				if (noAskCheckbox.checked) {
            					savePreference('deny');
            				}
            				cleanup();
            				resolve(false);
            			};

            			// 允许
            			dialog.querySelector('#btn-allow').onclick = (e) => {
            				e.stopPropagation();
            				if (noAskCheckbox.checked) {
            					savePreference('allow');
            				}
            				cleanup();
            				resolve(true);
            			};
            		});
            	}

            	// --- 核心执行逻辑：写入剪切板 ---
            	async function doRealCopy(text) {
            		try {
            			_isSelfCopy = true;

            			if (navigator.clipboard && navigator.clipboard.writeText) {
            				await navigator.clipboard.writeText(text);
            			} else {
            				const ta = document.createElement('textarea');
            				ta.value = text;
            				ta.style.position = 'fixed';
            				ta.style.opacity = '0';
            				document.body.appendChild(ta);
            				ta.select();
            				document.execCommand('copy');
            				document.body.removeChild(ta);
            			}
            		} catch (err) {
            			console.error('Copy failed:', err);
            		} finally {
            			_isSelfCopy = false;
            		}
            	}

            	// --- 拦截器 1: 拦截 Clipboard API ---
            	if (navigator.clipboard && navigator.clipboard.writeText) {
            		const originWriteText = navigator.clipboard.writeText;
            		navigator.clipboard.writeText = async function(text) {
            			if (_isSelfCopy) {
            				return originWriteText.call(navigator.clipboard, text);
            			}

            			// 检查持久化偏好
            			const pref = checkPreference();
            			if (pref === 'allow') {
            				_lastCopyTime = Date.now();
            				return doRealCopy(text);
            			} else if (pref === 'deny') {
            				// 用户已选择拒绝,直接阻止
            				throw new Error('User denied copy by preference');
            			}

            			const now = Date.now();
            			if (now - _lastCopyTime < COOLDOWN_MS) {
            				console.warn('复制太频繁，已拦截');
            				throw new Error('Copy too frequent');
            			}

            			const allow = await showCopyConfirm(text);
            			if (allow) {
            				_lastCopyTime = Date.now();
            				await doRealCopy(text);
            			} else {
            				throw new Error('User denied copy');
            			}
            		};
            	}

            	// --- 拦截器 2: 拦截 copy 事件 ---
            	document.addEventListener('copy', async function(e) {
            		if (_isSelfCopy) return;

            		e.preventDefault();
            		e.stopImmediatePropagation();

            		// 检查持久化偏好
            		const pref = checkPreference();
            		if (pref === 'allow') {
            			_lastCopyTime = Date.now();
            			await doRealCopy(window.getSelection()?.toString().trim() || '');
            			return;
            		} else if (pref === 'deny') {
            			// 用户已选择拒绝,直接阻止
            			return;
            		}

            		const selection = window.getSelection();
            		const text = selection ? selection.toString().trim() : '';
            		if (!text) return;

            		const now = Date.now();
            		if (now - _lastCopyTime < COOLDOWN_MS) return;

            		setTimeout(async () => {
            			const allow = await showCopyConfirm(text);
            			if (allow) {
            				_lastCopyTime = Date.now();
            				await doRealCopy(text);
            			}
            		}, 10);
            	}, true);

            })();
        """.trimIndent()

        webView.evaluateJavascript(jsCode, null)
    }

    override val key: String get() = GeneratedSettingList.INJECT_CONSOLE

    override val processes: Set<ActionProcess> get() = setOf(ActionProcess.TOOL)
}
