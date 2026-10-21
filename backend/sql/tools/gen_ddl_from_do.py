#!/usr/bin/env python3
"""根据 MyBatis-Plus DO 实体类生成 MySQL 建表语句。

用法：
    python3 backend/sql/tools/gen_ddl_from_do.py <DO 所在目录> <输出 .sql 文件>

规则：
- 表名取 @TableName，字段名驼峰转下划线；@TableField(exist = false) 与 static 字段跳过
- 继承 BaseDO 的表补充 creator / create_time / updater / update_time / deleted
- 未标注 @TenantIgnore 的表补充 tenant_id（多租户插件会自动带上该条件）
- 布尔字段默认 b'0'；字段注释取字段上方 Javadoc 的第一行
"""
import pathlib
import re
import sys

TYPE_MAP = {
    'Long': 'bigint',
    'long': 'bigint',
    'Integer': 'int',
    'int': 'int',
    'Boolean': 'bit(1)',
    'boolean': 'bit(1)',
    'BigDecimal': 'decimal(24, 6)',
    'LocalDateTime': 'datetime',
    'LocalDate': 'date',
}
LONG_TEXT = {'remark', 'content', 'description', 'detail_address', 'contact_last_content',
             'password', 'access_token', 'app_secret',  # 加密后的密钥会变长
             'file_url', 'llm_api_key', 'mail_signature', 'extra_instructions', 'reason_detail', 'question'}  # 较长的文本
# 正文类大字段，用 mediumtext（最大 16MB）
MEDIUM_TEXT = {'text_body', 'html_body', 'raw_headers', 'last_sync_error', 'to_addresses', 'cc_addresses'}
# 知识条目、AI 回复、跟进记录等正文
MEDIUM_TEXT |= {'content'}
# 知识库向量（JSON 数组，1536 维约 20KB）
MEDIUM_TEXT |= {'embedding'}
# AI 工作报告的统计 JSON 与总结
MEDIUM_TEXT |= {'stats', 'summary'}
# 工作时间：每周安排、节假日
MEDIUM_TEXT |= {'schedule', 'holidays', 'extra_workdays'}


def snake(name: str) -> str:
    return re.sub(r'(?<!^)(?=[A-Z])', '_', name).lower()


def sql_type(java_type: str, column: str) -> str:
    if java_type.startswith('List<') or java_type.startswith('Set<'):
        return 'varchar(2000)'
    if java_type == 'String':
        if column in MEDIUM_TEXT:
            return 'mediumtext'
        return 'varchar(1024)' if column in LONG_TEXT else 'varchar(255)'
    return TYPE_MAP.get(java_type, 'varchar(255)')  # 枚举等按字符串存


def parse(path: pathlib.Path):
    src = path.read_text(encoding='utf-8')
    m = re.search(r'@TableName\(\s*(?:value\s*=\s*)?"([^"]+)"', src)
    if not m:
        return None
    table = m.group(1)
    title = ''
    tm = re.search(r'/\*\*\s*\n\s*\*\s*([^\n@]+)', src[: src.find('public class')])
    if tm:
        title = tm.group(1).strip().rstrip('。')
    columns = []
    # 逐个字段：可选的 Javadoc + 注解 + private 声明
    pattern = re.compile(
        r'(?:/\*\*(?P<doc>(?:(?!\*/).)*?)\*/\s*)?(?P<ann>(?:@[A-Za-z]+(?:\([^)]*\))?\s*)*)'
        r'private\s+(?P<static>static\s+)?(?:final\s+)?(?P<type>[A-Za-z<>, ]+?)\s+(?P<name>\w+)\s*;',
        re.S,
    )
    body = src[src.find('public class'):]
    for f in pattern.finditer(body):
        if f.group('static') or 'exist = false' in (f.group('ann') or ''):
            continue
        name = f.group('name')
        col = snake(name)
        doc = (f.group('doc') or '').strip()
        first = ''
        for line in doc.splitlines():
            line = line.strip().lstrip('*').strip()
            if line and not line.startswith('@') and not line.startswith('{@'):
                first = line
                break
        columns.append((col, f.group('type').replace(' ', ''), first.replace("'", '')[:120]))
    tenant = '@TenantIgnore' not in src[: src.find('public class')]
    return table, title, columns, tenant


def ddl(table, title, columns, tenant=True):
    lines = []
    for col, jt, comment in columns:
        if col == 'id':
            lines.append("  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号'")
            continue
        t = sql_type(jt, col)
        default = " DEFAULT b'0'" if t == 'bit(1)' else ''  # 布尔字段默认 false，与上游建表习惯一致
        lines.append(f"  `{col}` {t} NULL{default} COMMENT '{comment}'")
    lines += [
        "  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者'",
        "  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'",
        "  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者'",
        "  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间'",
        "  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除'",
    ]
    if tenant:
        lines.append("  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号'")
    lines.append('  PRIMARY KEY (`id`)')
    return (
        f"DROP TABLE IF EXISTS `{table}`;\n"
        f"CREATE TABLE `{table}` (\n" + ',\n'.join(lines) + "\n"
        f") ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '{title}';\n"
    )


def main():
    src_dir, out = pathlib.Path(sys.argv[1]), pathlib.Path(sys.argv[2])
    parts = [
        '-- 由 backend/sql/tools/gen_ddl_from_do.py 根据 DO 实体类生成，请勿手改；实体变更后重新生成',
        'SET NAMES utf8mb4;',
        '',
    ]
    count = 0
    for path in sorted(src_dir.rglob('*DO.java')):
        parsed = parse(path)
        if parsed:
            parts.append(ddl(*parsed))
            count += 1
    out.write_text('\n'.join(parts), encoding='utf-8')
    print(f'{count} 张表 -> {out}')


if __name__ == '__main__':
    main()
