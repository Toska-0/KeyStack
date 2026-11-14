package com.toska.keystack

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption

private const val MAX_WORKSPACE_FILE_BYTES = 256 * 1024
private const val MAX_WORKSPACE_CONTEXT_CHARS = 60_000

internal fun buildWorkspacePrompt(workspacePath: String): String {
    if (workspacePath.isBlank()) return ""
    val root = runCatching { Paths.get(workspacePath).toRealPath() }.getOrNull()
        ?: return "Рабочая папка KeyStack недоступна: путь не существует или нет прав доступа."
    if (!Files.isDirectory(root)) return "Рабочая папка KeyStack не является каталогом."

    val snapshot = StringBuilder()
    var included = 0
    runCatching {
        Files.walk(root, 4).use { stream ->
            val iterator = stream.iterator()
            while (iterator.hasNext() && snapshot.length < MAX_WORKSPACE_CONTEXT_CHARS) {
                val file: java.nio.file.Path = iterator.next()
                if (!Files.isRegularFile(file) || Files.isSymbolicLink(file) ||
                    file.fileName.toString().startsWith(".")) continue
                val extension = file.fileName.toString().substringAfterLast('.', "").lowercase()
                if (extension !in setOf("txt", "md", "json", "csv", "xml", "html", "htm", "css", "js", "ts", "kt", "java", "py", "yaml", "yml", "toml", "ini", "log")) continue
                runCatching {
                    if (Files.size(file) <= MAX_WORKSPACE_FILE_BYTES) {
                        val text = Files.readString(file)
                        val relative = root.relativize(file).toString().replace(File.separatorChar, '/')
                        snapshot.append("\n--- Файл: ").append(relative).append(" ---\n")
                            .append(text.take(12_000)).append("\n")
                        included++
                    }
                }
            }
        }
    }

    return """
Ты работаешь в приложении KeyStack для Windows.
Рабочая папка пользователя: $root
Разрешено читать файлы из этой папки и создавать/изменять файлы только внутри неё.
Не обращайся к файлам вне неё и не используй .., абсолютные пути или ссылки для обхода ограничения.
Ниже передан снимок текстовых файлов (до 4 уровней вложенности, с ограничением размера). Используй его как содержимое файлов.
Чтобы создать или полностью перезаписать файл, добавь в конец ответа отдельный блок строго такого вида:
```keystack-write path="путь/относительно/рабочей-папки.txt"
Полное новое содержимое файла
```
Можно выдать несколько таких блоков. Не помещай их внутрь других блоков кода. Перед блоком кратко объясни, что будет записано.
Файлы сохраняются локально. Не создавай и не изменяй файлы без прямой просьбы пользователя.
Если нужного файла нет в снимке, сообщи об этом и попроси пользователя выбрать папку заново или отправить файл в чат.
Текстовых файлов в контексте: $included
$snapshot
""".trim()
}

internal data class ProposedWorkspaceWrite(val relativePath: String, val content: String)

internal fun extractWorkspaceWrites(response: String): Pair<String, List<ProposedWorkspaceWrite>> {
    val block = Regex("""(?s)```keystack-write\s+path="([^"]+)"\s*\n(.*?)\n```""")
    val proposals = mutableListOf<ProposedWorkspaceWrite>()
    val visible = block.replace(response) { match ->
        proposals += ProposedWorkspaceWrite(match.groupValues[1], match.groupValues[2])
        ""
    }.trim()
    return visible to proposals
}

internal fun applyWorkspaceWrite(workspacePath: String, proposal: ProposedWorkspaceWrite): String {
    if (workspacePath.isBlank()) return "Рабочая папка не выбрана"
    val root = runCatching { Paths.get(workspacePath).toRealPath() }.getOrElse { return "Рабочая папка недоступна" }
    if (!Files.isDirectory(root)) return "Рабочая папка не является каталогом"
    val relative = proposal.relativePath
    return runCatching {
        val rel = Paths.get(relative)
        require(!rel.isAbsolute) { "Абсолютные пути запрещены" }
        require(rel.none { it.toString() == ".." }) { "Переходы через .. запрещены" }
        val target = root.resolve(rel).normalize()
        require(target.startsWith(root) && target != root) { "Путь выходит за пределы рабочей папки" }
        require(proposal.content.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "Файл превышает 1 МБ" }
        Files.createDirectories(target.parent)
        require(target.parent.toRealPath().startsWith(root)) { "Символическая ссылка за пределы рабочей папки запрещена" }
        if (Files.exists(target)) {
            require(!Files.isSymbolicLink(target) && target.toRealPath().startsWith(root)) { "Запись через символическую ссылку запрещена" }
            val backupDir = root.resolve(".keystack-backups")
            require(!Files.isSymbolicLink(backupDir)) { "Каталог резервных копий не может быть символической ссылкой" }
            Files.createDirectories(backupDir)
            require(backupDir.toRealPath().startsWith(root)) { "Каталог резервных копий выходит за пределы рабочей папки" }
            Files.copy(target, backupDir.resolve(target.fileName.toString() + "." + System.currentTimeMillis() + ".bak"), StandardCopyOption.REPLACE_EXISTING)
        }
        val temp = Files.createTempFile(target.parent, ".keystack-", ".tmp")
        try {
            Files.writeString(temp, proposal.content, Charsets.UTF_8)
            try { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) { Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING) }
        } finally { Files.deleteIfExists(temp) }
        "Сохранено: " + root.relativize(target).toString().replace(File.separatorChar, '/')
    }.getOrElse { "Не удалось записать «$relative»: " + (it.message ?: "ошибка доступа") }
}
