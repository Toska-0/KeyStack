package com.toska.keystack

import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

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
        Files.walk(root, 4).use { paths ->
            paths.filter { Files.isRegularFile(it) }
                .filterNot { it.fileName.toString().startsWith(".") }
                .filter { it.fileName.toString().substringAfterLast('.', "").lowercase() in
                    setOf("txt", "md", "json", "csv", "xml", "html", "htm", "css", "js", "ts", "kt", "java", "py", "yaml", "yml", "toml", "ini", "log") }
                .sorted()
                .forEach { file ->
                    if (snapshot.length < MAX_WORKSPACE_CONTEXT_CHARS) {
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

internal fun applyWorkspaceWrites(workspacePath: String, response: String): Pair<String, List<String>> {
    if (workspacePath.isBlank()) return response to emptyList()
    val root = runCatching { Paths.get(workspacePath).toRealPath() }.getOrNull()
        ?: return response to emptyList()
    if (!Files.isDirectory(root)) return response to emptyList()

    val block = Regex("""(?s)```keystack-write\s+path="([^"]+)"\s*\n(.*?)\n```""")
    val results = mutableListOf<String>()
    val visible = block.replace(response) { match ->
        val relative = match.groupValues[1]
        val body = match.groupValues[2]
        val result = runCatching {
            val rel = Paths.get(relative)
            require(!rel.isAbsolute) { "Абсолютные пути запрещены" }
            val target = root.resolve(rel).normalize()
            require(target.startsWith(root) && target != root) { "Путь выходит за пределы рабочей папки" }
            require(body.toByteArray(Charsets.UTF_8).size <= 1_000_000) { "Файл превышает 1 МБ" }
            Files.createDirectories(target.parent)
            val realParent = target.parent.toRealPath()
            require(realParent.startsWith(root)) { "Символическая ссылка за пределы рабочей папки запрещена" }
            Files.writeString(target, body, Charsets.UTF_8)
            "Создан/обновлён: " + root.relativize(target).toString().replace(File.separatorChar, '/')
        }.getOrElse { "Не удалось записать «$relative»: " + (it.message ?: "ошибка доступа") }
        results += result
        ""
    }.trim()
    return visible to results
}
