package com.toska.keystack

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DesktopRegressionTest {
    @Test
    fun ordinaryPromptsDoNotTriggerWebSearch() {
        assertFalse(WebSearch.shouldSearch("Объясни, как работает оперативная память"))
        assertFalse(WebSearch.shouldSearch("Сравни два подхода к программированию"))
        assertTrue(WebSearch.shouldSearch("Найди последние новости о Kotlin"))
    }

    @Test
    fun workspaceWriteBlocksAreExtractedButNotApplied() {
        val fence = "\u0060\u0060\u0060"
        val response = "Готово.\n\n" + fence + "keystack-write path=\"notes/result.txt\"\nHello world\n" + fence
        val (visible, proposals) = extractWorkspaceWrites(response)
        assertEquals("Готово.", visible)
        assertEquals(1, proposals.size)
        assertEquals("notes/result.txt", proposals.single().relativePath)
        assertEquals("Hello world", proposals.single().content)
    }

    @Test
    fun workspaceWritesRejectTraversalAndBackupExistingFiles() {
        val root = Files.createTempDirectory("keystack-workspace").toFile()
        val outside = File(root.parentFile, "keystack-test-outside.txt")
        val rejected = applyWorkspaceWrite(root.absolutePath, ProposedWorkspaceWrite("../" + outside.name, "unsafe"))
        assertTrue(rejected.startsWith("Не удалось записать"))
        assertFalse(outside.exists())

        val target = File(root, "notes.txt")
        target.writeText("old")
        val saved = applyWorkspaceWrite(root.absolutePath, ProposedWorkspaceWrite("notes.txt", "new"))
        assertTrue(saved.startsWith("Сохранено"))
        assertEquals("new", target.readText())
        val backupDir = File(root, ".keystack-backups")
        assertTrue(backupDir.listFiles()?.any { it.readText() == "old" } == true)
    }

    @Test
    fun imagesAreStoredOutsideChatJsonAndRestoredOnLoad() = runBlocking {
        val dir = Files.createTempDirectory("keystack-chat").toFile()
        val image = "data:image/png;base64," + Base64.getEncoder().encodeToString(byteArrayOf(1, 2, 3, 4))
        val session = ChatSession(
            id = "chat-test",
            createdAt = 1L,
            updatedAt = 1L,
            messages = listOf(StoredMessage(role = "user", content = "", images = listOf(image)))
        )
        val store = ChatStore(dir)
        store.save(listOf(session))
        val json = File(dir, "chats.json").readText()
        assertFalse(json.contains(image))
        assertTrue(json.contains("keystack-image:"))
        val restored = store.load().single().messages.single().images.single()
        assertEquals(image, restored)
    }

    @Test
    fun corruptedChatFileRecoversFromBackup() = runBlocking {
        val dir = Files.createTempDirectory("keystack-backup").toFile()
        val store = ChatStore(dir)
        val first = ChatSession(id = "first", createdAt = 1L, updatedAt = 1L)
        val second = ChatSession(id = "second", createdAt = 2L, updatedAt = 2L)
        store.save(listOf(first))
        store.save(listOf(second))
        File(dir, "chats.json").writeText("{broken")
        val recovered = store.load()
        assertEquals("first", recovered.single().id)
    }
}
