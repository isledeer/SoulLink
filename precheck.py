#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
SoulLink native 代码质检脚本（precheck）。
扫描 native/app/src/main/java/com/isledeer/soullink/ 下的所有 .kt 文件：
  1. 中文标识符（排除 // 注释行、块注释行与字符串字面量）
  2. 粘连 import（一行内出现多个 import）
  3. 基础导入缺失（MaterialTheme / stateIn / Column）
  4. 嵌套滚动警告（LazyColumn 内再出现 LazyColumn 或 verticalScroll）

退出码：0 = 通过；1 = 存在错误。警告不阻断。
"""
import os
import re
import sys

ROOT = os.path.join(
    os.path.dirname(os.path.abspath(__file__)),
    "native",
    "app",
    "src",
    "main",
    "java",
    "com",
    "isledeer",
    "soullink",
)

CJK = re.compile(r"[\u4e00-\u9fa5]")
IMPORT = re.compile(r"^import\s+")

errors = []
warnings = []


def code_text(line: str) -> str:
    """去掉插值块、字符串字面量与行尾注释，便于检查中文标识符"""
    no_comment = line.split("//", 1)[0]
    # 先去掉 ${...} 插值（内部可能嵌套引号与中文）
    no_interp = re.sub(r"\$\{[^}]*\}", "", no_comment)
    # 再把成对引号之间的内容清空（简单状态机，兼容转义）
    out = []
    in_str = False
    i = 0
    while i < len(no_interp):
        ch = no_interp[i]
        if ch == '"':
            out.append('""')
            i += 1
            while i < len(no_interp) and no_interp[i] != '"':
                if no_interp[i] == "\\":
                    i += 2
                else:
                    i += 1
            if i < len(no_interp):
                i += 1
        else:
            out.append(ch)
            i += 1
    return "".join(out)


files = []
for dirpath, _, names in os.walk(ROOT):
    for name in names:
        if name.endswith(".kt"):
            files.append(os.path.join(dirpath, name))

for path in sorted(files):
    text = open(path, encoding="utf-8").read()
    lines = text.split("\n")
    rel = os.path.relpath(path, ROOT)

    # ---------- 规则 1：中文标识符 ----------
    in_block_comment = False
    for i, raw in enumerate(lines, 1):
        stripped = raw.strip()
        if in_block_comment:
            if "*/" in stripped:
                in_block_comment = False
            continue
        if stripped.startswith("/*"):
            in_block_comment = True
            continue
        if stripped.startswith("//"):
            continue
        if stripped.startswith("*"):
            continue
        if CJK.search(code_text(raw)):
            errors.append(f"{rel}:{i} 中文残留: {stripped[:70]}")

    # ---------- 规则 2：粘连 import ----------
    for i, raw in enumerate(lines, 1):
        stripped = raw.strip()
        if stripped.startswith("import") and raw.count("import") > 1:
            errors.append(f"{rel}:{i} 粘连 import: {stripped[:70]}")

    # ---------- 规则 3：基础导入缺失（精确 import 或包级通配 import 均可） ----------
    def missing(name: str, import_line: str, wildcard: str) -> bool:
        uses = any(
            re.search(rf"\b{re.escape(name)}\b", code_text(line))
            for line in lines
            if not line.strip().startswith("import")
        )
        return uses and import_line not in text and wildcard not in text

    if missing("MaterialTheme", "import androidx.compose.material3.MaterialTheme", "import androidx.compose.material3.*"):
        errors.append(f"{rel} 使用了 MaterialTheme 但缺少对应 import")
    if missing("stateIn", "import kotlinx.coroutines.flow.stateIn", "import kotlinx.coroutines.flow.*"):
        errors.append(f"{rel} 使用了 stateIn 但缺少对应 import")
    if missing("Column", "import androidx.compose.foundation.layout.Column", "import androidx.compose.foundation.layout.*"):
        errors.append(f"{rel} 使用了 Column 但缺少对应 import")

    # ---------- 规则 4.5：疑似缺 @Composable 的函数（警告） ----------
    prev_annotated = True
    for i, raw in enumerate(lines, 1):
        stripped = raw.strip()
        if stripped.startswith("@"):
            prev_annotated = True
            continue
        m = re.match(r"^(?:private |internal |public )?fun\s+", stripped)
        if m:
            prev_annotated = ("@Composable" in raw) or prev_annotated
        if stripped.startswith("fun ") and "Modifier" not in raw:
            pass

    # ---------- 规则 4：嵌套滚动（警告） ----------
    if text.count("LazyColumn(") > 1 or (
        "LazyColumn(" in text and ".verticalScroll(" in text
    ):
        warnings.append(f"{rel} 疑似嵌套滚动（LazyColumn 内再放滚动容器），注意无限高度崩溃")


print(f"扫描 {len(files)} 个 .kt 文件")
if errors:
    print("\n❌ 质检未通过：")
    for e in errors:
        print(f"  {e}")
    sys.exit(1)

if warnings:
    print("\n⚠️ 警告（不阻断）：")
    for w in warnings:
        print(f"  {w}")

print("\n✅ 质检通过，无低级错误")