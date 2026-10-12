# db/ 目录怎么用

**这个目录里的文件不是一个「按顺序跑一遍」的部署流程。** 每个文件服务一种库状态，
跑错状态就是清数据。跑之前先在下表里找到你的库属于哪种情况。

这份文档是 2026-10-08 之后补的：那天 `init.sql` 被整份跑到了一个有数据的线上库上，
63 张表被 DROP 重建，`sys_user` / `message_template` 归零。当时 `init.sql` 头部
**写了**警告、`种子数据.sql` 头部**写反了**安全性，所以问题不是「没人写说明」，
而是「说明不在被读到的位置」——注释挡不住手快。于是这里做两件事：
把适用场合集中成一张表，并让每个文件的自检数字可以核对。

---

## 一张表：哪个文件、什么时候跑

| 文件 | 能重复跑吗 | 什么时候跑 | 什么时候**绝对不能**跑 |
|---|---|---|---|
| `init.sql` | ❌ 不能 | 只有**空库**，整份跑，一次建好 76 张表 | 任何有数据的库。尤其线上——76 条 `DROP TABLE`，跑完数据全没 |
| `种子数据.sql` | ⚠️ 分段不同 | 见下方「按段拆分」 | 已有数据的库**整份**跑；段 0~3B 单独跑（见下） |
| `存量修复.sql` | ✅ 可重复 | 结构或数据**落后于 init.sql** 的旧库；必须**在 `种子数据.sql` 之后**、必须加 `--force` | 空库（到处报 1060）；不先跑 `种子数据.sql` 就跑它（`sys_user.major_id` 会回填成旧 id） |

> 2026-10 整理：原 10 份「补*.sql」散文件已全部归并——建表 DDL 进 `init.sql`
> （空库全量）与 `存量修复.sql` 段 1.16（老库幂等差量），纯数据种子进
> `种子数据.sql` 段 5B/9/10/11，原文件已删。本目录现在只有上面三个 SQL。

### `种子数据.sql` 按段拆分（这是最容易出事的一份）

| 段 | 内容 | 对已有数据的库 |
|---|---|---|
| 0 | 备份 `bak_college_` / `bak_major_` | ⚠️ **会先 DROP 掉上一次的备份再重建**。跑第二遍就把上一次留下的旧数据副本删了 |
| 1~3 | college / major 整表替换 + 全库引用改绑 | ⚠️ 自建的、不在清单里的学院/专业会被 DELETE，不会回来 |
| 3B | subject / major_subject 整表替换 | ⚠️ 管理端自建的学科会被 DELETE，且引用它的 `paper_info.subject_id` 悬空 |
| 4~7、5B | 操作类型字典 / 知识库分类 / 消息模板 / 查重规则 / 15 篇知识库文章 | ✅ 按唯一键 `ON DUPLICATE KEY UPDATE` / `INSERT IGNORE`，可重复执行 |
| 8 | 自检（段 1~7） | ✅ 只读 |
| 9 | 审核意见模板 review_template（10 条公开模板） | ✅ `ON DUPLICATE KEY UPDATE`，不动 `usage_count` / `last_used` |
| 10 | 论文全生命周期字典（70xx 论文状态 / 71xx 评阅 / 72xx 答辩 / 73xx 学位 / 74xx 评价标签） | ✅ `INSERT IGNORE` |
| 11 | sys_role 4 个角色 + 超管账号 | ✅ `ON DUPLICATE KEY UPDATE` |

所以：**已有数据的库，只跑段 4~7、5B、9~11。** 只要不是存心要那份 20 学院 / 114 专业的
清单，就不要碰段 0~3B。

---

## 冷启动：空库

```
init.sql              -- 建表，76 张（含论文全生命周期 P1~P6 的 11 张新表与全部新列）
种子数据.sql          -- 段 1~11 全段（段 11 建 4 个角色 + 1 个超管账号）
```

## 已有库：缺什么补什么

```
1. 备份          mysqldump 整库先拉一份
2. 探针          SELECT COUNT(*) FROM <每张可能有问题的表>;   -- 先看缺什么
                 SELECT COUNT(*) FROM bak_college_20261007;   -- 表不存在报 1146 属正常
                 -- 非空就先单独 dump 出来，再决定要不要跑 种子数据.sql
3. 按缺补         缺账号/角色 → 种子数据.sql 段 11
                 缺消息模板 → 种子数据.sql 段 6
                 缺查重规则 → 种子数据.sql 段 7
                 缺帮助中心文章 → 种子数据.sql 段 5B
                 缺审核意见模板 → 种子数据.sql 段 9
                 缺全生命周期字典 → 种子数据.sql 段 10
                 缺学院/专业 → 种子数据.sql 段 0~3（空表上 DELETE 删不到东西，
                               但库里有旧数据时先备份，段 3 会把引用改绑到新 id）
4. 对数字        每份跑完看自检，数字不对就停下，不要接着跑下一份
```

`存量修复.sql` 只在这批症状出现时才需要（`Unknown column 'college_name'` /
`teacher_audit` 表不存在 / 日志中心三张卡 500 / 阈值列表恒空等），
适用症状清单写在它自己头部，不要「顺手跑一下」。

---

## 跑完该看到的数字

| 文件 | 自检位置 | 期望 |
|---|---|---|
| `种子数据.sql` | 段 8.1 | `colleges=20  majors=114  orphan_majors=0` |
| | 段 8.3 | `old_sys_user=0  old_paper_info=0  old_student_info=0`（旧 id 无残留引用） |
| | 段 8.4 | `operation_types=139  knowledge_categories=6  message_templates=19` |
| | 段 8.5 | `seed_rules>=4`，`default_rules` **恰好 1**（0 → 学生端卡片 404；>1 → 取到 id 最大那条） |
| | 段 8.6 | `missing` 为空 |
| | 段 8.7 | `subjects=100  level1=10  level2=90  orphan_subject=0` |
| | 段 8.8 | `dangling_should_be_0=0` |
| | 段 5B | `articles=15  orphan_articles=0  published=15` |
| | 段 9 | `seed_public=10`（`templates>=10`，教师自建会让它更大，属正常） |
| | 段 10 | 字典 `13 / 9 / 17 / 3 / 7`（paper_status / 评阅 / 答辩 / 学位 / 评价标签） |
| | 段 11 | `roles=4  users=1` |
| `存量修复.sql` | 段 5 | 断言全部达标；**只看数字，不看有没有 ERROR 行**（1060/1061/1054 是「已执行过」的信号） |

---

## 绝对不要做

- **不要在线上跑 `init.sql` 整份。**（2026-10-08 事故的直接原因）
- 不要在有数据的库上整份跑 `种子数据.sql`。
- 不要在没先把 `bak_college_*` / `bak_major_*` 导出来的情况下跑段 0——它会 DROP 掉备份表。
- 不要先跑 `存量修复.sql` 再跑 `种子数据.sql`，顺序反了会回填成旧的专业 id。
- 不要不加 `--force` 跑 `存量修复.sql`——MySQL 8 不支持 `ADD COLUMN IF NOT EXISTS`，
  客户端默认遇错即停，会在第一条 1060 处中断、后面所有段一条都不执行。
- 不要用 `mysql -e "..."` 或往那台机器的终端里粘贴含中文的 SQL（字符集会把中文写坏）；
  用文件重定向 + `--default-character-set=utf8mb4`。
- **不要把「没有报错」当成「跑成功了」。** 0 字节的文件喂给 mysql 是静默成功；
  判断依据只有自检数字。写完文件先 `wc -c` 看一眼大小。

## 备份相关的两个坑

- `system_param.backup_enabled` 为空 / 为 0 时，凌晨 2 点的自动备份**不会跑**
  （`AutoBackupScheduler` 看这个开关）。手工备份前先确认这个值。
- 段 0 建出来的 `bak_college_20261007` / `bak_major_20261007` **不在** `init.sql`
  的 DROP 清单里，可能躲过 `init.sql` 活下来。已经踩过一次，跑任何东西之前先探一下。

---

## 已知遗留问题（尚未修，遇到别惊讶）

1. 段 3B 是整表替换，管理端自建的学科会被删（段 3B 注释里已标注）。
2. `compare_lib` 表和 `category_similarity_threshold` 表**没有任何种子数据**，
   管理端建查重规则时这两处可能卡住。
3. **论文类型不需要种子数据，这是刻意的。** 它的真相源是后端
   `common/enums/PaperTypeEnum`，`/api/dict/data/type/paper_type`（以及
   `/api/dict/label`、`/api/dict/paper/all`）由 `DictService` 在查库**之前**
   特判该 dictType 并用枚举现合成返回。所以 `sys_dict_data` 里**没有**
   `paper_type` 行是正常状态，不要往 `种子数据.sql` 里补 —— 补了也读不到，
   只会变成第二套可能漂移的值域。判断标准很简单：空库起服务后
   `/api/dict/paper/all` 的 `paperType` 应返回 3 条（毕业论文/课程论文/其他）。
   同理，论文类型的合法取值校验走 `@ValidPaperType`（Bean Validation），
   也不依赖任何库表数据。
