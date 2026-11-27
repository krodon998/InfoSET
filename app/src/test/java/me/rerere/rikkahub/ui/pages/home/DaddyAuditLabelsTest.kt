package me.rerere.rikkahub.ui.pages.home

import org.junit.Assert.assertEquals
import org.junit.Test

class DaddyAuditLabelsTest {
    @Test
    fun `known audit codes are shown in Chinese`() {
        assertEquals("授予权限", auditActionLabel("GRANT"))
        assertEquals("读取 GitHub 文件", auditActionLabel("GITHUB_FILE_READ"))
        assertEquals("GitHub MCP · 读取文件", auditActionLabel("GITHUB_MCP_GET_FILE_CONTENTS"))
        assertEquals("成功", auditResultLabel("SUCCEEDED"))
        assertEquals("已拒绝", auditResultLabel("DENIED"))
        assertEquals("读取 GitHub 仓库", auditCapabilityLabel("GITHUB_REPOSITORY_READ"))
    }
}
