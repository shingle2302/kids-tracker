package com.shingle.kids.tracker

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class MyAccessibilityService : AccessibilityService() {

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val rootNode = rootInActiveWindow ?: return
        
        // 监控范围：设置页面和安装器页面
        val packageName = event.packageName?.toString() ?: ""
        if (packageName.contains("settings") || packageName.contains("packageinstaller")) {
            
            // 查找是否正在查看“本应用”的详情页
            // “儿童安全守护”是我们在 strings.xml 中定义的应用名
            if (findNodeByText(rootNode, "儿童安全守护")) {
                // 如果在详情页发现了“卸载”或“清除数据”按钮，进行拦截
                if (findNodeByText(rootNode, "卸载") || 
                    findNodeByText(rootNode, "Uninstall") || 
                    findNodeByText(rootNode, "清除数据") || 
                    findNodeByText(rootNode, "存储")) {
                    
                    // 1. 强制返回桌面，中断卸载行为
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    
                    // 2. 触发警报拨号
                    triggerAlert()
                }
            }
        }
    }

    private fun findNodeByText(node: AccessibilityNodeInfo, text: String): Boolean {
        if (node.text?.toString()?.contains(text, ignoreCase = true) == true) return true
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null && findNodeByText(child, text)) return true
        }
        return false
    }

    private fun triggerAlert() {
        val prefs = getSharedPreferences("config", Context.MODE_PRIVATE)
        val phoneNumber = prefs.getString("phone", "")
        if (!phoneNumber.isNullOrEmpty()) {
            val intent = Intent(Intent.ACTION_CALL)
            intent.data = Uri.parse("tel:$phoneNumber")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            try {
                startActivity(intent)
            } catch (e: Exception) {
                val dialIntent = Intent(Intent.ACTION_DIAL)
                dialIntent.data = Uri.parse("tel:$phoneNumber")
                dialIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(dialIntent)
            }
        }
    }

    override fun onInterrupt() {}
}
