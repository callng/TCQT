# proto2json 严格模式使用指南

本文介绍 `proto2json` 的严格解析模式 `ProtoDecodeMode.WIRE_PRESERVING`，包括基本读取、嵌套消息、字符串、原始字节、packed repeated、RAW 数字类型解释，以及修改后重新写回父消息的方法。

## 1. 严格模式是什么

严格模式只根据 Protobuf wire format 解码，不主动推断 schema 层类型。

调用方式：

```kotlin
val root = ProtoUtils.decodeFromByteArray(
    data,
    ProtoDecodeMode.WIRE_PRESERVING
)
```

严格模式下主要会得到以下类型：

| Wire type | 解码结果                                       |
|-----------|--------------------------------------------|
| 0         | `ProtoNumber(ProtoNumberType.RAW_VARINT)`  |
| 1         | `ProtoNumber(ProtoNumberType.RAW_FIXED64)` |
| 2         | `ProtoByteString`                          |
| 3 / 4     | `ProtoGroup`                               |
| 5         | `ProtoNumber(ProtoNumberType.RAW_FIXED32)` |
| 同字段重复出现   | `ProtoList`                                |

其中最需要手动解释的是 wire type 2，因为它可能代表：

- `string`
- `bytes`
- 嵌套 message
- packed repeated

缺少 `.proto`、descriptor 或其他 schema 信息时，二进制数据自身无法可靠区分这些类型。

---

## 2. 推荐的辅助函数

下面这些扩展可以简化严格模式的读取逻辑。

```kotlin
private fun ProtoByteString.decodeStrictMessage(): ProtoMap {
    return ProtoUtils.decodeFromByteString(
        value,
        ProtoDecodeMode.WIRE_PRESERVING
    )
}

private fun ProtoByteString.decodeStrictMessageOrNull(): ProtoMap? {
    return runCatching {
        decodeStrictMessage()
    }.getOrNull()
}

private fun ProtoMap.messageField(tag: Int): ProtoMap? {
    val field = getOrNull(tag) as? ProtoByteString
        ?: return null

    return field.decodeStrictMessageOrNull()
}

private fun ProtoMap.utf8Field(tag: Int): String? {
    val field = getOrNull(tag) as? ProtoByteString
        ?: return null

    if (!field.isValidUtf8()) {
        return null
    }

    return field.toUtfString()
}

private fun ProtoMap.repeatedMessageField(tag: Int): List<ProtoMap> {
    return when (val field = getOrNull(tag)) {
        is ProtoByteString -> {
            listOfNotNull(field.decodeStrictMessageOrNull())
        }

        is ProtoList -> {
            field.value.mapNotNull { item ->
                (item as? ProtoByteString)
                    ?.decodeStrictMessageOrNull()
            }
        }

        else -> emptyList()
    }
}
```

这里显式传入：

```kotlin
ProtoDecodeMode.WIRE_PRESERVING
```

可以确保嵌套消息继续使用严格模式解析，不会在下一层切回默认兼容模式。

---

## 3. 示例：解析 ThemeEngine 响应

假设协议结构为：

```text
root
└── 6: message
    └── 2: message
        ├── 3: string
        ├── 8: string 或 message
        └── 11: varint
```

严格模式读取：

```kotlin
val root = ProtoUtils.decodeFromByteArray(
    data,
    ProtoDecodeMode.WIRE_PRESERVING
)

val field6 = root.messageField(6)
    ?: error("响应缺少 message 字段 6")

val field2 = field6.messageField(2)
    ?: error("响应缺少 message 字段 6.2")

val code = (field2.getOrNull(11) as? ProtoNumber)
    ?.toInt()
    ?: error("响应缺少 varint 字段 6.2.11")

val md5 = field2.utf8Field(3).orEmpty()
```

字段 11 在严格模式下会被解析为：

```kotlin
ProtoNumberType.RAW_VARINT
```

如果你已经确认它是状态码，直接调用：

```kotlin
number.toInt()
```

即可。

---

## 4. 解析“可能是字符串，也可能是嵌套消息”的字段

ThemeEngine 中的 tag 8 可能存在两种结构：

```text
8: string
```

或者：

```text
8: message {
    7: string
    12: string
}
```

严格模式下，这两种形式最外层都会得到 `ProtoByteString`。

可以根据已知子字段进行判断：

```kotlin
private fun parseStrictUrl(field2: ProtoMap): String {
    val raw = field2.getOrNull(8) as? ProtoByteString
        ?: return ""

    val possibleMessage = raw.decodeStrictMessageOrNull()

    if (
        possibleMessage != null &&
        (possibleMessage.has(7) || possibleMessage.has(12))
    ) {
        val tag7 = possibleMessage.utf8Field(7).orEmpty()
        val tag12 = possibleMessage.utf8Field(12).orEmpty()

        return if (tag7.isEmpty() && tag12.isEmpty()) {
            ""
        } else {
            "https:/$tag7$tag12"
        }
    }

    return if (raw.isValidUtf8()) {
        raw.toUtfString()
    } else {
        ""
    }
}
```

使用：

```kotlin
val baseUrl = parseStrictUrl(field2)
```

仅仅能被 `UnknownFieldSet.parseFrom()` 接受，并不能证明某段 bytes 一定是嵌套消息。更可靠的做法是结合已知字段号、协议结构或业务约束进行判断。

---

## 5. 完整 ThemeEngine 严格解析示例

```kotlin
private data class ThemeResult(
    val code: Int,
    val md5: String,
    val url: String
)

private fun parseThemeResponseStrict(data: ByteArray): ThemeResult {
    val root = ProtoUtils.decodeFromByteArray(
        data,
        ProtoDecodeMode.WIRE_PRESERVING
    )

    val responseBody = root
        .messageField(6)
        ?.messageField(2)
        ?: error("响应字段 6.2 缺失或不是有效 message")

    val code = (responseBody.getOrNull(11) as? ProtoNumber)
        ?.toInt()
        ?: error("响应字段 6.2.11 缺失")

    return ThemeResult(
        code = code,
        md5 = responseBody.utf8Field(3).orEmpty(),
        url = parseStrictUrl(responseBody)
    )
}
```

---

## 6. 示例：严格解析群管理员列表

假设协议结构为：

```text
root
├── 3: result code
└── 4: message
    └── 4: repeated message
        ├── 1: uint64 uin
        └── 18: enum/int32 role
```

完整解析代码：

```kotlin
private fun parseGroupAdminsStrict(data: ByteArray): List<String> {
    val root = ProtoUtils.decodeFromByteArray(
        data,
        ProtoDecodeMode.WIRE_PRESERVING
    )

    val resultCode = (root.getOrNull(3) as? ProtoNumber)
        ?.toInt()
        ?: error("响应缺少 result code")

    if (resultCode != 0) {
        error("服务器返回错误码：$resultCode")
    }

    val body = root.messageField(4)
        ?: return emptyList()

    val members = body.repeatedMessageField(4)

    val owner = mutableListOf<String>()
    val admins = mutableListOf<String>()

    members.forEach { member ->
        val uin = (member.getOrNull(1) as? ProtoNumber)
            ?.toULong()
            ?.toString()
            ?: return@forEach

        val role = (member.getOrNull(18) as? ProtoNumber)
            ?.toInt()
            ?: return@forEach

        when (role) {
            1 -> owner += uin
            2 -> admins += uin
        }
    }

    return owner + admins
}
```

### repeated 字段的表示

只有一个成员时，字段可能直接是：

```kotlin
ProtoByteString
```

有多个成员时，字段会是：

```kotlin
ProtoList(
    ProtoByteString(...),
    ProtoByteString(...)
)
```

因此 `repeatedMessageField()` 同时处理了两种情况。

---

## 7. 读取普通字符串

假设 tag 5 已知是 protobuf `string`：

```kotlin
val root = ProtoUtils.decodeFromByteArray(
    data,
    ProtoDecodeMode.WIRE_PRESERVING
)

val bytes = root.getOrNull(5) as? ProtoByteString
    ?: error("字段 5 缺失")

require(bytes.isValidUtf8()) {
    "字段 5 包含非法 UTF-8"
}

val text = bytes.toUtfString()
```

也可以使用前面的辅助函数：

```kotlin
val text = root.utf8Field(5)
```

---

## 8. 读取原始 bytes

假设 tag 6 是文件内容、图片、压缩数据、密钥或其他二进制内容：

```kotlin
val bytes = (root.getOrNull(6) as? ProtoByteString)
    ?.toByteArray()
    ?: error("字段 6 缺失")
```

此时直接获取原始字节即可，不要调用：

```kotlin
toUtfString()
decodeStrictMessage()
```

除非你已经知道该字段的具体 schema 类型。

---

## 9. 解析 packed repeated

假设 schema 为：

```proto
repeated uint32 values = 5 [packed = true];
```

严格模式下 tag 5 会得到 `ProtoByteString`：

```kotlin
val raw = root.getOrNull(5) as? ProtoByteString
    ?: error("字段 5 缺失")

val packed = raw.decodeAsPacked(
    ProtoPackedType.UINT32
)

val values = packed.value.value.map { item ->
    item.asUInt
}
```

也可以简写：

```kotlin
val values = raw
    .decodeAsPacked(ProtoPackedType.UINT32)
    .map { it.asUInt }
```

如果 schema 是：

```proto
repeated sint32 values = 5 [packed = true];
```

必须指定：

```kotlin
ProtoPackedType.SINT32
```

packed payload 自身没有元素类型信息，因此严格模式无法自动区分：

- `uint32`
- `sint32`
- `enum`
- `bool`
- 其他可 packed 标量类型

---

## 10. 解释 RAW_VARINT

wire type 0 可能代表：

- `int32`
- `int64`
- `uint32`
- `uint64`
- `sint32`
- `sint64`
- `bool`
- `enum`

严格模式统一产生：

```kotlin
ProtoNumberType.RAW_VARINT
```

根据已知 schema 解释：

```kotlin
val raw = root.getOrNull(1) as? ProtoNumber
    ?: error("字段不存在")

val int32Value = raw.toInt()
val int64Value = raw.toLong()

val uint32Value = raw.toUInt()
val uint64Value = raw.toULong()

val boolValue = raw.toLong() != 0L
val enumNumber = raw.toInt()
```

### 解释 sint32 和 sint64

`sint32` 与 `sint64` 使用 ZigZag 编码，需要额外解码：

```kotlin
import com.google.protobuf.CodedInputStream

val sint32Value = CodedInputStream.decodeZigZag32(
    raw.toInt()
)

val sint64Value = CodedInputStream.decodeZigZag64(
    raw.toLong()
)
```

---

## 11. 解释 RAW_FIXED32

wire type 5 可能代表：

- `fixed32`
- `sfixed32`
- `float`

严格模式会得到：

```kotlin
ProtoNumberType.RAW_FIXED32
```

解释方式：

```kotlin
val raw = root.getOrNull(2) as? ProtoNumber
    ?: error("字段 2 缺失")

val fixed32Value: UInt = raw.toUInt()
val sfixed32Value: Int = raw.toInt()
val floatValue: Float = Float.fromBits(raw.toInt())
```

例如相同的四个字节：

```text
00 00 80 3F
```

根据 schema 可以解释为：

```kotlin
fixed32 = 1065353216u
sfixed32 = 1065353216
float = 1.0f
```

wire 数据自身无法说明哪个解释正确。

---

## 12. 解释 RAW_FIXED64

wire type 1 可能代表：

- `fixed64`
- `sfixed64`
- `double`

严格模式会得到：

```kotlin
ProtoNumberType.RAW_FIXED64
```

解释方式：

```kotlin
val raw = root.getOrNull(3) as? ProtoNumber
    ?: error("字段 3 缺失")

val fixed64Value: ULong = raw.toULong()
val sfixed64Value: Long = raw.toLong()
val doubleValue: Double = Double.fromBits(raw.toLong())
```

---

## 13. 修改严格解码得到的嵌套消息

严格模式不会自动把手动解析出的子消息写回父对象。

下面这样修改：

```kotlin
val child = raw.decodeStrictMessage()
child[5] = "new name"
```

只修改了 `child`，父消息里仍然保留原始的 `ProtoByteString`。

因此需要显式写回：

```kotlin
val root = ProtoUtils.decodeFromByteArray(
    data,
    ProtoDecodeMode.WIRE_PRESERVING
)

val field4Bytes = root.getOrNull(4) as? ProtoByteString
    ?: error("字段 4 缺失")

val field4 = field4Bytes.decodeStrictMessage()

val field10Bytes = field4.getOrNull(10) as? ProtoByteString
    ?: error("字段 4.10 缺失")

val field10 = field10Bytes.decodeStrictMessage()

field10[5] = "new name"

// 从最内层开始逐层写回父消息
field4[10] = field10
root[4] = field4

val newData = root.toByteArray()
```

`ProtoMap` 自身实现了 length-delimited message 编码，因此：

```kotlin
field4[10] = field10
```

会自动重新计算嵌套消息长度。

---

## 14. 修改路径 `4 → 10 → 40 → 1 → 5`

完整示例：

```kotlin
val root = ProtoUtils.decodeFromByteArray(
    data,
    ProtoDecodeMode.WIRE_PRESERVING
)

val m4 = (root[4] as ProtoByteString).decodeStrictMessage()
val m10 = (m4[10] as ProtoByteString).decodeStrictMessage()
val m40 = (m10[40] as ProtoByteString).decodeStrictMessage()
val m1 = (m40[1] as ProtoByteString).decodeStrictMessage()

val oldName = (m1[5] as ProtoByteString).toUtfString()
val newName = fixSuffix(oldName)

if (newName != oldName) {
    m1[5] = newName

    m40[1] = m1
    m10[40] = m40
    m4[10] = m10
    root[4] = m4

    msfRspInfo.pbBuffer = root.toByteArray()
}
```

严格模式要求每一步类型都由调用者明确决定，修改后也需要逐层写回。

---

## 15. 什么时候适合严格模式

严格模式适合：

- 检查原始 wire 类型
- 调试协议字段
- 分析未知数据
- 区分 fixed32、fixed64 和 varint
- 避免误把普通 bytes 当成子消息
- 按已知 schema 精确控制解析方式
- 做协议逆向或诊断工具

调用方式：

```kotlin
val root = ProtoUtils.decodeFromByteArray(
    data,
    ProtoDecodeMode.WIRE_PRESERVING
)
```

---

## 16. 什么时候使用兼容模式

日常业务读取更适合默认兼容模式：

```kotlin
val root = ProtoUtils.decodeFromByteArray(data)
```

然后继续使用简洁路径 API：

```kotlin
root.has(4, 10, 40, 1, 5)
root[6, 2] as? ProtoMap
member.asMap
```

兼容模式适合 Hook 业务代码和已知协议结构的日常读取。

严格模式更适合协议分析、调试与 wire 层诊断。

---

## 17. 简单选择建议

### 普通业务代码

```kotlin
val root = ProtoUtils.decodeFromByteArray(data)
```

特点：

- 写法简单
- 支持多级路径
- 嵌套消息自动递归解析

### 协议分析代码

```kotlin
val root = ProtoUtils.decodeFromByteArray(
    data,
    ProtoDecodeMode.WIRE_PRESERVING
)
```

特点：

- 保留真实 wire 类型
- 不主动推断 length-delimited 内容
- 需要调用者依据 schema 手动解释
- 适合逆向、调试和诊断
