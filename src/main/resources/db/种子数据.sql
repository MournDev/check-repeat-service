-- ============================================================================
-- 种子数据 —— 本仓库唯一的初始数据来源，可重复执行。
--
-- ⚠️ 本文件不是「什么库都能整份跑」。按库的状态分三种用法：
--
--   · 空库 —— 按顺序跑 init.sql → 本文件，段 1~11 全跑，得到一套可用数据。
--
--   · 就是要换成这份清单的库 —— 段 1~11 全跑。段 1~3 把 college / major 整表
--     替换成下面这份 20 / 114 清单、段 3 顺带把全库引用从旧 id 改绑到新 id；
--     段 3B 同样整表替换 subject / major_subject。
--
--   · 其余已有数据的库 —— **只跑段 4~7、5B、9~11**。这些段（操作类型字典 /
--     知识库分类与文章 / 消息模板 / 查重规则 / 审核意见模板 / 全生命周期字典 /
--     内置角色与超管账号）按各自的唯一键 upsert 或 INSERT IGNORE，
--     重复执行不会插出重复行。不要跑段 0~3B：
--       · 段 0 先 DROP 掉 bak_college/bak_major 再重建 —— 跑第二遍就把上一次
--         留下的备份毁了，那是唯一的旧数据副本；
--       · 段 1~3 是「DELETE 整表 → 插写死的固定 id → 改绑引用」，库里自建的、
--         不在这份清单里的学院 / 专业会被顶掉；
--       · 段 3B 是同样的「DELETE 整表 → 插固定 id」，会把管理端自建的学科连同
--         major_subject 映射一起删掉（引用它的 paper_info.subject_id 随之悬空）。
--
--   这一条以前写的是「已有数据的库也可以直接跑：每段都按唯一键 upsert」，
--   那是错的 —— 段 0~3B 不是 upsert。其它脚本的适用场合见 README.md。
--
-- 内容与顺序（顺序即依赖关系）：
--   1~3  college / major 基础数据 + 全库引用改绑  ← 整表替换语义，说明见段 1
--  3B    subject / major_subject  学科领域 + 专业↔学科映射（取代 sys_dict_data 的字符串树）
--   4    sys_operation_type    操作类型字典（日志中心「模块使用率」靠它分类）
--   5    knowledge_category    知识库分类（knowledge_article.category_id 指向它）
--  5B    knowledge_article     帮助中心 15 篇文章（INSERT IGNORE，不回写界面改动）
--   6    message_template      消息模板（缺哪个 code，对应的站内信就发不出去）
--   7    check_rule            查重规则（缺 is_default=1 那条，学生端规则卡片直接 404）
--   8    自检（段 1~7）
--   9    review_template       审核意见模板（10 条公开模板，覆盖全部 5 个分类）
--   10   全生命周期字典        paper_status 70xx / 评阅 71xx / 答辩 72xx / 学位 73xx / 导师评价标签 74xx
--   11   sys_role + sys_user   4 个内置角色 + 超级管理员账号
--
--   3B 放在 3 之后，是要先让 college/major 的固定 id 就位 —— 专业映射写的就是这些 id。
--
-- 存量库（V8 之前建的、结构落后于 init.sql 的）请在跑完本文件之后，再跑同目录的
-- 存量修复.sql —— 那里第 1 段要从本文件改绑过的 teacher_info / student_info 回填
-- sys_user.major_id，顺序反了会回填成旧 id。
-- ============================================================================

SET NAMES utf8mb4;   -- 本文件含中文，客户端按 latin1 解析会写进乱码


-- ============================================================================
-- 段 1~3：学院 / 专业 基础数据（整表替换）+ 全库引用改绑
--
-- 为什么是「替换」而不是纯 INSERT：
--   uk_college_code / uk_major_code 是单列唯一键、不含 is_deleted，
--   软删后重插同名编码会直接撞唯一键，所以换数据只能硬删。
--   student_info.college_id 上有全库唯一一条真外键（→ college.id），
--   段内临时关闭外键检查、执行完还原。
--
-- 这套语义**只在两种库上成立**，且都可重复执行：
--   空库        —— DELETE 删不到东西，直接灌入 20 / 114；
--   就是要换成  —— 旧数据被替换，段 3 把引用从旧 id 改绑到新 id；
--   这份清单的库
--   再跑一遍    —— DELETE 掉自己上一遍插的行、再插回同样的固定 id，
--                 段 3 的 CASE 全部不命中（已经是新 id），结果不变。
--
-- ⚠️ 已经有数据的库，默认**不要跑段 0~3**：这一段不区分「清单里的学院」和
--    「库里自建的学院」，两样一起 DELETE。只要不是存心要这份 20 / 114 清单，
--    就跳过后面的段 3B、只跑段 4~7 —— 那四段才是「按唯一键 upsert、可重复执行」。
-- ============================================================================

-- ---------- 0) 备份 ----------
-- ⚠️ 这里的 DROP 是**真会删备份的**：第二次跑本文件，先把上一次留下的
--    bak_college_/bak_major_ 连同里面的旧数据一起删掉，再按当前（已经被替换过的）
--    college / major 重建 —— 于是那份唯一的旧数据副本就没了，且没法恢复。
--    要跑第二遍之前，先把这两张表导出来存别处。
--    这两张 bak_ 表不在 init.sql 的 DROP 清单里，所以它们有可能躲过 init.sql 活下来；
--    动手前先探一下：
--      SELECT COUNT(*) FROM bak_college_20261007;  -- 表不存在会报 1146，正常
--      SELECT COUNT(*) FROM bak_major_20261007;
--    非空就先 mysqldump 出来，再决定要不要跑本段。
DROP TABLE IF EXISTS bak_college_20261007;
DROP TABLE IF EXISTS bak_major_20261007;
CREATE TABLE bak_college_20261007 AS SELECT * FROM college;
CREATE TABLE bak_major_20261007   AS SELECT * FROM major;

SET @op := 2060661729410551809;                 -- 操作人ID（超级管理员），按需修改
SET @fk := @@FOREIGN_KEY_CHECKS;
SET FOREIGN_KEY_CHECKS = 0;

START TRANSACTION;

-- ---------- 1) 清空旧数据 ----------
DELETE FROM major;
DELETE FROM college;

-- ---------- 2) 学院 ----------
INSERT INTO college (id, create_by, create_time, update_by, update_time, is_deleted, delete_by, delete_time, college_code, college_name, college_desc) VALUES
(1668573000000, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'CS', '计算机科学与技术学院', '计算机学院'),
(1668573000001, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'SE', '软件学院', '软件学院'),
(1668573000002, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'CYBER', '网络空间安全学院', '网安学院'),
(1668573000003, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'EE', '电子信息与电气工程学院', '电信学院'),
(1668573000004, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'ME', '机械与动力工程学院', '机动学院'),
(1668573000005, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'MSE', '材料科学与工程学院', '材料学院'),
(1668573000006, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'CIVIL', '土木工程学院', '土木学院'),
(1668573000007, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'CHEM', '化学与化工学院', '化工学院'),
(1668573000008, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'LIFE', '生命科学学院', '生科学院'),
(1668573000009, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'MATH', '数学与统计学院', '数统学院'),
(1668573000010, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'PHYS', '物理与光电工程学院', '物光学院'),
(1668573000011, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'ECON', '经济与管理学院', '经管学院'),
(1668573000012, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'LAW', '法学院', '法学院'),
(1668573000013, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'FL', '外国语学院', '外语学院'),
(1668573000014, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'HUM', '文学院', '文学院'),
(1668573000015, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'JC', '新闻与传播学院', '新传学院'),
(1668573000016, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'ART', '艺术与设计学院', '艺术学院'),
(1668573000017, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'MED', '医学院', '医学院'),
(1668573000018, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'ENV', '环境与资源学院', '环资学院'),
(1668573000019, @op, NOW(), @op, NOW(), 0, NULL, NULL, 'PE', '体育学院', '体育学院');

-- ---------- 3) 专业 ----------
INSERT INTO major (id, create_by, create_time, update_by, update_time, is_deleted, delete_by, delete_time, major_code, major_name, college_id, major_desc, user_id, current_advisor_count, max_advisor_count) VALUES
(1668573001000, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080901', '计算机科学与技术', 1668573000000, '计算机软硬件与系统能力培养', NULL, 0, 10),
(1668573001001, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080905', '物联网工程', 1668573000000, '传感网、嵌入式与物联网系统', NULL, 0, 10),
(1668573001002, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080907T', '智能科学与技术', 1668573000000, '智能信息处理与机器学习', NULL, 0, 10),
(1668573001003, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080910T', '数据科学与大数据技术', 1668573000000, '大数据采集、存储与分析', NULL, 0, 10),
(1668573001004, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080902', '软件工程', 1668573000001, '软件系统设计、开发与项目管理', NULL, 0, 10),
(1668573001005, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080903', '网络工程', 1668573000001, '网络规划、部署与运维', NULL, 0, 10),
(1668573001006, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080906T', '数字媒体技术', 1668573000001, '数字内容制作与交互技术', NULL, 0, 10),
(1668573001007, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080904K', '信息安全', 1668573000002, '信息系统安全防护与攻防对抗', NULL, 0, 10),
(1668573001008, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080911TK', '网络空间安全', 1668573000002, '网络空间安全理论与技术', NULL, 0, 10),
(1668573001009, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080914TK', '保密技术', 1668573000002, '保密管理与保密技术', NULL, 0, 10),
(1668573001010, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080918TK', '密码科学与技术', 1668573000002, '密码算法、协议与应用', NULL, 0, 10),
(1668573001011, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080601', '电气工程及其自动化', 1668573000003, '电力系统、电机与电气控制', NULL, 0, 10),
(1668573001012, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080701', '电子信息工程', 1668573000003, '电子系统与信息处理', NULL, 0, 10),
(1668573001013, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080702', '电子科学与技术', 1668573000003, '电子器件、电路与系统', NULL, 0, 10),
(1668573001014, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080703', '通信工程', 1668573000003, '通信系统与网络技术', NULL, 0, 10),
(1668573001015, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080704', '微电子科学与工程', 1668573000003, '半导体器件与集成电路工艺', NULL, 0, 10),
(1668573001016, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080710T', '集成电路设计与集成系统', 1668573000003, '芯片设计与集成系统开发', NULL, 0, 10),
(1668573001017, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080717T', '人工智能', 1668573000003, '机器学习、计算机视觉与智能系统', NULL, 0, 10),
(1668573001018, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080201', '机械工程', 1668573000004, '机械设计与制造基础', NULL, 0, 10),
(1668573001019, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080202', '机械设计制造及其自动化', 1668573000004, '机械设计、制造与自动化控制', NULL, 0, 10),
(1668573001020, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080204', '机械电子工程', 1668573000004, '机电一体化与运动控制', NULL, 0, 10),
(1668573001021, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080207', '车辆工程', 1668573000004, '汽车设计与车辆动力学', NULL, 0, 10),
(1668573001022, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080213T', '智能制造工程', 1668573000004, '智能产线与数字化制造', NULL, 0, 10),
(1668573001023, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080216T', '新能源汽车工程', 1668573000004, '新能源汽车动力与电控', NULL, 0, 10),
(1668573001024, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080501', '能源与动力工程', 1668573000004, '热能、动力机械与能源系统', NULL, 0, 10),
(1668573001025, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080401', '材料科学与工程', 1668573000005, '材料结构、性能与制备', NULL, 0, 10),
(1668573001026, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080402', '材料物理', 1668573000005, '材料物理性能与表征', NULL, 0, 10),
(1668573001027, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080405', '金属材料工程', 1668573000005, '金属材料成型与热处理', NULL, 0, 10),
(1668573001028, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080407', '高分子材料与工程', 1668573000005, '高分子合成与加工', NULL, 0, 10),
(1668573001029, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080412T', '功能材料', 1668573000005, '光电、储能等功能材料', NULL, 0, 10),
(1668573001030, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080414T', '新能源材料与器件', 1668573000005, '电池、光伏材料与器件', NULL, 0, 10),
(1668573001031, @op, NOW(), @op, NOW(), 0, NULL, NULL, '081001', '土木工程', 1668573000006, '结构、施工与工程管理', NULL, 0, 10),
(1668573001032, @op, NOW(), @op, NOW(), 0, NULL, NULL, '081002', '建筑环境与能源应用工程', 1668573000006, '暖通空调与建筑节能', NULL, 0, 10),
(1668573001033, @op, NOW(), @op, NOW(), 0, NULL, NULL, '081003', '给排水科学与工程', 1668573000006, '水处理与水系统设计', NULL, 0, 10),
(1668573001034, @op, NOW(), @op, NOW(), 0, NULL, NULL, '081006T', '道路桥梁与渡河工程', 1668573000006, '路基路面与桥梁结构', NULL, 0, 10),
(1668573001035, @op, NOW(), @op, NOW(), 0, NULL, NULL, '081008T', '智能建造', 1668573000006, 'BIM、装配式与建造信息化', NULL, 0, 10),
(1668573001036, @op, NOW(), @op, NOW(), 0, NULL, NULL, '082801', '建筑学', 1668573000006, '建筑设计及其理论', NULL, 0, 10),
(1668573001037, @op, NOW(), @op, NOW(), 0, NULL, NULL, '120103', '工程管理', 1668573000006, '工程项目投资与进度管理', NULL, 0, 10),
(1668573001038, @op, NOW(), @op, NOW(), 0, NULL, NULL, '070301', '化学', 1668573000007, '化学基础理论与实验', NULL, 0, 10),
(1668573001039, @op, NOW(), @op, NOW(), 0, NULL, NULL, '070302', '应用化学', 1668573000007, '精细化学品与工业分析', NULL, 0, 10),
(1668573001040, @op, NOW(), @op, NOW(), 0, NULL, NULL, '081301', '化学工程与工艺', 1668573000007, '化工过程与反应工程', NULL, 0, 10),
(1668573001041, @op, NOW(), @op, NOW(), 0, NULL, NULL, '081302', '制药工程', 1668573000007, '药物合成与制剂工程', NULL, 0, 10),
(1668573001042, @op, NOW(), @op, NOW(), 0, NULL, NULL, '081304T', '能源化学工程', 1668573000007, '能源转化与催化', NULL, 0, 10),
(1668573001043, @op, NOW(), @op, NOW(), 0, NULL, NULL, '081308T', '精细化工', 1668573000007, '精细化学品合成与配方', NULL, 0, 10),
(1668573001044, @op, NOW(), @op, NOW(), 0, NULL, NULL, '071001', '生物科学', 1668573000008, '生命科学基础理论与实验', NULL, 0, 10),
(1668573001045, @op, NOW(), @op, NOW(), 0, NULL, NULL, '071002', '生物技术', 1668573000008, '基因工程与发酵技术', NULL, 0, 10),
(1668573001046, @op, NOW(), @op, NOW(), 0, NULL, NULL, '071003', '生物信息学', 1668573000008, '组学数据分析与建模', NULL, 0, 10),
(1668573001047, @op, NOW(), @op, NOW(), 0, NULL, NULL, '071004', '生态学', 1668573000008, '生态系统与生物多样性', NULL, 0, 10),
(1668573001048, @op, NOW(), @op, NOW(), 0, NULL, NULL, '083001', '生物工程', 1668573000008, '生物过程与生物制造', NULL, 0, 10),
(1668573001049, @op, NOW(), @op, NOW(), 0, NULL, NULL, '083002T', '生物制药', 1668573000008, '生物药物研发与生产', NULL, 0, 10),
(1668573001050, @op, NOW(), @op, NOW(), 0, NULL, NULL, '070101', '数学与应用数学', 1668573000009, '数学基础理论与应用', NULL, 0, 10),
(1668573001051, @op, NOW(), @op, NOW(), 0, NULL, NULL, '070102', '信息与计算科学', 1668573000009, '计算数学与科学计算', NULL, 0, 10),
(1668573001052, @op, NOW(), @op, NOW(), 0, NULL, NULL, '070104T', '数据计算及应用', 1668573000009, '数据建模与算法实现', NULL, 0, 10),
(1668573001053, @op, NOW(), @op, NOW(), 0, NULL, NULL, '071201', '统计学', 1668573000009, '统计理论与数据分析', NULL, 0, 10),
(1668573001054, @op, NOW(), @op, NOW(), 0, NULL, NULL, '071202', '应用统计学', 1668573000009, '统计方法在行业中的应用', NULL, 0, 10),
(1668573001055, @op, NOW(), @op, NOW(), 0, NULL, NULL, '070201', '物理学', 1668573000010, '物理基础理论与实验', NULL, 0, 10),
(1668573001056, @op, NOW(), @op, NOW(), 0, NULL, NULL, '070202', '应用物理学', 1668573000010, '物理原理的工程应用', NULL, 0, 10),
(1668573001057, @op, NOW(), @op, NOW(), 0, NULL, NULL, '070203', '核物理', 1668573000010, '原子核物理与核技术', NULL, 0, 10),
(1668573001058, @op, NOW(), @op, NOW(), 0, NULL, NULL, '080705', '光电信息科学与工程', 1668573000010, '光电子器件与光电系统', NULL, 0, 10),
(1668573001059, @op, NOW(), @op, NOW(), 0, NULL, NULL, '020101', '经济学', 1668573000011, '经济学理论与政策分析', NULL, 0, 10),
(1668573001060, @op, NOW(), @op, NOW(), 0, NULL, NULL, '020301K', '金融学', 1668573000011, '货币银行、投资与风险管理', NULL, 0, 10),
(1668573001061, @op, NOW(), @op, NOW(), 0, NULL, NULL, '020302', '金融工程', 1668573000011, '金融建模与量化分析', NULL, 0, 10),
(1668573001062, @op, NOW(), @op, NOW(), 0, NULL, NULL, '020401', '国际经济与贸易', 1668573000011, '国际贸易实务与跨境电商', NULL, 0, 10),
(1668573001063, @op, NOW(), @op, NOW(), 0, NULL, NULL, '120102', '信息管理与信息系统', 1668573000011, '信息系统规划与数据管理', NULL, 0, 10),
(1668573001064, @op, NOW(), @op, NOW(), 0, NULL, NULL, '120108T', '大数据管理与应用', 1668573000011, '商业数据分析与智能决策', NULL, 0, 10),
(1668573001065, @op, NOW(), @op, NOW(), 0, NULL, NULL, '120201K', '工商管理', 1668573000011, '企业战略与运营管理', NULL, 0, 10),
(1668573001066, @op, NOW(), @op, NOW(), 0, NULL, NULL, '120203K', '会计学', 1668573000011, '财务会计与审计', NULL, 0, 10),
(1668573001067, @op, NOW(), @op, NOW(), 0, NULL, NULL, '120204', '财务管理', 1668573000011, '投融资决策与财务分析', NULL, 0, 10),
(1668573001068, @op, NOW(), @op, NOW(), 0, NULL, NULL, '120206', '人力资源管理', 1668573000011, '招聘、绩效与薪酬管理', NULL, 0, 10),
(1668573001069, @op, NOW(), @op, NOW(), 0, NULL, NULL, '030101K', '法学', 1668573000012, '法学基础理论与法律实务', NULL, 0, 10),
(1668573001070, @op, NOW(), @op, NOW(), 0, NULL, NULL, '030102T', '知识产权', 1668573000012, '专利、商标与著作权保护', NULL, 0, 10),
(1668573001071, @op, NOW(), @op, NOW(), 0, NULL, NULL, '030105T', '国际经贸规则', 1668573000012, '国际经贸条约与争端解决', NULL, 0, 10),
(1668573001072, @op, NOW(), @op, NOW(), 0, NULL, NULL, '030108TK', '纪检监察', 1668573000012, '纪检监察理论与实务', NULL, 0, 10),
(1668573001073, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050201', '英语', 1668573000013, '英语语言文学与翻译', NULL, 0, 10),
(1668573001074, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050203', '德语', 1668573000013, '德语语言文学', NULL, 0, 10),
(1668573001075, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050204', '法语', 1668573000013, '法语语言文学', NULL, 0, 10),
(1668573001076, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050207', '日语', 1668573000013, '日语语言文学', NULL, 0, 10),
(1668573001077, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050261', '翻译', 1668573000013, '口笔译理论与实践', NULL, 0, 10),
(1668573001078, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050262', '商务英语', 1668573000013, '国际商务英语应用', NULL, 0, 10),
(1668573001079, @op, NOW(), @op, NOW(), 0, NULL, NULL, '010101', '哲学', 1668573000014, '哲学基础理论与思想史', NULL, 0, 10),
(1668573001080, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050101', '汉语言文学', 1668573000014, '中国语言文学与写作', NULL, 0, 10),
(1668573001081, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050103', '汉语国际教育', 1668573000014, '对外汉语教学与文化传播', NULL, 0, 10),
(1668573001082, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050107T', '秘书学', 1668573000014, '文秘实务与办公管理', NULL, 0, 10),
(1668573001083, @op, NOW(), @op, NOW(), 0, NULL, NULL, '060101', '历史学', 1668573000014, '中国史、世界史与史学理论', NULL, 0, 10),
(1668573001084, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050301', '新闻学', 1668573000015, '新闻采写编评与新闻理论', NULL, 0, 10),
(1668573001085, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050302', '广播电视学', 1668573000015, '视听内容策划与制作', NULL, 0, 10),
(1668573001086, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050303', '广告学', 1668573000015, '广告策划与品牌传播', NULL, 0, 10),
(1668573001087, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050306T', '网络与新媒体', 1668573000015, '新媒体运营与内容生产', NULL, 0, 10),
(1668573001088, @op, NOW(), @op, NOW(), 0, NULL, NULL, '050309T', '国际新闻与传播', 1668573000015, '国际新闻采编与跨文化传播', NULL, 0, 10),
(1668573001089, @op, NOW(), @op, NOW(), 0, NULL, NULL, '130202', '音乐学', 1668573000016, '音乐理论、表演与教学', NULL, 0, 10),
(1668573001090, @op, NOW(), @op, NOW(), 0, NULL, NULL, '130310', '动画', 1668573000016, '动画创作与数字影像', NULL, 0, 10),
(1668573001091, @op, NOW(), @op, NOW(), 0, NULL, NULL, '130401', '美术学', 1668573000016, '美术创作与理论研究', NULL, 0, 10),
(1668573001092, @op, NOW(), @op, NOW(), 0, NULL, NULL, '130502', '视觉传达设计', 1668573000016, '品牌视觉与平面设计', NULL, 0, 10),
(1668573001093, @op, NOW(), @op, NOW(), 0, NULL, NULL, '130503', '环境设计', 1668573000016, '室内与景观空间设计', NULL, 0, 10),
(1668573001094, @op, NOW(), @op, NOW(), 0, NULL, NULL, '130504', '产品设计', 1668573000016, '工业产品造型与交互设计', NULL, 0, 10),
(1668573001095, @op, NOW(), @op, NOW(), 0, NULL, NULL, '130508', '数字媒体艺术', 1668573000016, '数字影像与交互艺术', NULL, 0, 10),
(1668573001096, @op, NOW(), @op, NOW(), 0, NULL, NULL, '100101K', '基础医学', 1668573000017, '人体结构与病理基础', NULL, 0, 10),
(1668573001097, @op, NOW(), @op, NOW(), 0, NULL, NULL, '100201K', '临床医学', 1668573000017, '临床诊疗与医学实践', NULL, 0, 10),
(1668573001098, @op, NOW(), @op, NOW(), 0, NULL, NULL, '100301K', '口腔医学', 1668573000017, '口腔疾病诊疗与修复', NULL, 0, 10),
(1668573001099, @op, NOW(), @op, NOW(), 0, NULL, NULL, '100401K', '预防医学', 1668573000017, '疾病预防与公共卫生', NULL, 0, 10),
(1668573001100, @op, NOW(), @op, NOW(), 0, NULL, NULL, '100701', '药学', 1668573000017, '药物研发、生产与合理用药', NULL, 0, 10),
(1668573001101, @op, NOW(), @op, NOW(), 0, NULL, NULL, '100801', '中药学', 1668573000017, '中药鉴定、炮制与药理', NULL, 0, 10),
(1668573001102, @op, NOW(), @op, NOW(), 0, NULL, NULL, '101005', '康复治疗学', 1668573000017, '康复评定与治疗技术', NULL, 0, 10),
(1668573001103, @op, NOW(), @op, NOW(), 0, NULL, NULL, '101101K', '护理学', 1668573000017, '临床护理与护理管理', NULL, 0, 10),
(1668573001104, @op, NOW(), @op, NOW(), 0, NULL, NULL, '070503', '人文地理与城乡规划', 1668573000018, '区域规划与国土空间', NULL, 0, 10),
(1668573001105, @op, NOW(), @op, NOW(), 0, NULL, NULL, '070504', '地理信息科学', 1668573000018, 'GIS 与遥感技术应用', NULL, 0, 10),
(1668573001106, @op, NOW(), @op, NOW(), 0, NULL, NULL, '081403', '资源勘查工程', 1668573000018, '地质资源勘查与评价', NULL, 0, 10),
(1668573001107, @op, NOW(), @op, NOW(), 0, NULL, NULL, '082502', '环境工程', 1668573000018, '污染防治与水处理工程', NULL, 0, 10),
(1668573001108, @op, NOW(), @op, NOW(), 0, NULL, NULL, '082503', '环境科学', 1668573000018, '环境监测与评价', NULL, 0, 10),
(1668573001109, @op, NOW(), @op, NOW(), 0, NULL, NULL, '082504', '环境生态工程', 1668573000018, '生态修复与环境生态设计', NULL, 0, 10),
(1668573001110, @op, NOW(), @op, NOW(), 0, NULL, NULL, '040201', '体育教育', 1668573000019, '学校体育教学与训练', NULL, 0, 10),
(1668573001111, @op, NOW(), @op, NOW(), 0, NULL, NULL, '040203', '社会体育指导与管理', 1668573000019, '群众体育组织与指导', NULL, 0, 10),
(1668573001112, @op, NOW(), @op, NOW(), 0, NULL, NULL, '040205', '运动人体科学', 1668573000019, '运动生理与运动康复', NULL, 0, 10),
(1668573001113, @op, NOW(), @op, NOW(), 0, NULL, NULL, '040207T', '休闲体育', 1668573000019, '休闲运动项目策划与管理', NULL, 0, 10);

-- ---------- 4) 引用改绑：外键 id ----------
UPDATE sys_user SET college_id = CASE college_id
  WHEN 1668572928001 THEN 1668573000001
  WHEN 1668572928003 THEN 1668573000004
  WHEN 1668572928005 THEN 1668573000007
  WHEN 1668572928007 THEN 1668573000011
  WHEN 1668572928008 THEN 1668573000012
  ELSE college_id END, update_by = @op, update_time = NOW()
WHERE college_id IN (1668572928001, 1668572928003, 1668572928005, 1668572928007, 1668572928008);

UPDATE paper_info SET college_id = CASE college_id
  WHEN 1668572928001 THEN 1668573000001
  WHEN 1668572928003 THEN 1668573000004
  WHEN 1668572928005 THEN 1668573000007
  WHEN 1668572928007 THEN 1668573000011
  WHEN 1668572928008 THEN 1668573000012
  ELSE college_id END, update_by = @op, update_time = NOW()
WHERE college_id IN (1668572928001, 1668572928003, 1668572928005, 1668572928007, 1668572928008);

UPDATE student_info SET college_id = CASE college_id
  WHEN 1668572928001 THEN 1668573000001
  WHEN 1668572928003 THEN 1668573000004
  WHEN 1668572928005 THEN 1668573000007
  WHEN 1668572928007 THEN 1668573000011
  WHEN 1668572928008 THEN 1668573000012
  ELSE college_id END, update_by = @op, update_time = NOW()
WHERE college_id IN (1668572928001, 1668572928003, 1668572928005, 1668572928007, 1668572928008);

UPDATE teacher_allocation_record SET college_id = CASE college_id
  WHEN 1668572928001 THEN 1668573000001
  WHEN 1668572928003 THEN 1668573000004
  WHEN 1668572928005 THEN 1668573000007
  WHEN 1668572928007 THEN 1668573000011
  WHEN 1668572928008 THEN 1668573000012
  ELSE college_id END, update_by = @op, update_time = NOW()
WHERE college_id IN (1668572928001, 1668572928003, 1668572928005, 1668572928007, 1668572928008);

UPDATE paper_info SET major_id = CASE major_id
  WHEN 1668572928015 THEN 1668573001004
  WHEN 1668572928026 THEN 1668573001020
  WHEN 1668572928045 THEN 1668573001065
  WHEN 1668572928049 THEN 1668573001068
  WHEN 1668572928051 THEN 1668573001070
  WHEN 1668572928054 THEN 1668573001069
  ELSE major_id END, update_by = @op, update_time = NOW()
WHERE major_id IN (1668572928015, 1668572928026, 1668572928045, 1668572928049, 1668572928051, 1668572928054);

UPDATE student_info SET major_id = CASE major_id
  WHEN 1668572928015 THEN 1668573001004
  WHEN 1668572928026 THEN 1668573001020
  WHEN 1668572928045 THEN 1668573001065
  WHEN 1668572928049 THEN 1668573001068
  WHEN 1668572928051 THEN 1668573001070
  WHEN 1668572928054 THEN 1668573001069
  ELSE major_id END, update_by = @op, update_time = NOW()
WHERE major_id IN (1668572928015, 1668572928026, 1668572928045, 1668572928049, 1668572928051, 1668572928054);

UPDATE teacher_info SET major_id = CASE major_id
  WHEN 1668572928015 THEN 1668573001004
  WHEN 1668572928026 THEN 1668573001020
  WHEN 1668572928045 THEN 1668573001065
  WHEN 1668572928049 THEN 1668573001068
  WHEN 1668572928051 THEN 1668573001070
  WHEN 1668572928054 THEN 1668573001069
  ELSE major_id END, update_by = @op, update_time = NOW()
WHERE major_id IN (1668572928015, 1668572928026, 1668572928045, 1668572928049, 1668572928051, 1668572928054);

UPDATE teacher_allocation_record SET major_id = CASE major_id
  WHEN 1668572928015 THEN 1668573001004
  WHEN 1668572928026 THEN 1668573001020
  WHEN 1668572928045 THEN 1668573001065
  WHEN 1668572928049 THEN 1668573001068
  WHEN 1668572928051 THEN 1668573001070
  WHEN 1668572928054 THEN 1668573001069
  ELSE major_id END, update_by = @op, update_time = NOW()
WHERE major_id IN (1668572928015, 1668572928026, 1668572928045, 1668572928049, 1668572928051, 1668572928054);

-- ---------- 5) 引用改绑：冗余文本列 ----------
UPDATE student_info SET college_name = CASE college_name
  WHEN '软件工程学院' THEN '软件学院'
  WHEN '机械工程学院' THEN '机械与动力工程学院'
  WHEN '化学工程学院' THEN '化学与化工学院'
  WHEN '商学院' THEN '经济与管理学院'
  ELSE college_name END, update_by = @op, update_time = NOW()
WHERE college_name IN ('软件工程学院', '机械工程学院', '化学工程学院', '商学院');

UPDATE teacher_info SET college_name = CASE college_name
  WHEN '软件工程学院' THEN '软件学院'
  WHEN '机械工程学院' THEN '机械与动力工程学院'
  WHEN '化学工程学院' THEN '化学与化工学院'
  WHEN '商学院' THEN '经济与管理学院'
  ELSE college_name END, update_by = @op, update_time = NOW()
WHERE college_name IN ('软件工程学院', '机械工程学院', '化学工程学院', '商学院');

UPDATE teacher_allocation_record SET college_name = CASE college_name
  WHEN '软件工程学院' THEN '软件学院'
  WHEN '机械工程学院' THEN '机械与动力工程学院'
  WHEN '化学工程学院' THEN '化学与化工学院'
  WHEN '商学院' THEN '经济与管理学院'
  ELSE college_name END, update_by = @op, update_time = NOW()
WHERE college_name IN ('软件工程学院', '机械工程学院', '化学工程学院', '商学院');

UPDATE student_info SET major = CASE major
  WHEN '软件技术' THEN '软件工程'
  WHEN '民事诉讼法' THEN '法学'
  ELSE major END, update_by = @op, update_time = NOW()
WHERE major IN ('软件技术', '民事诉讼法');

UPDATE teacher_info SET major = CASE major
  WHEN '软件技术' THEN '软件工程'
  WHEN '民事诉讼法' THEN '法学'
  ELSE major END, update_by = @op, update_time = NOW()
WHERE major IN ('软件技术', '民事诉讼法');

UPDATE teacher_allocation_record SET major_name = CASE major_name
  WHEN '软件技术' THEN '软件工程'
  WHEN '民事诉讼法' THEN '法学'
  ELSE major_name END, update_by = @op, update_time = NOW()
WHERE major_name IN ('软件技术', '民事诉讼法');

-- 以下三处引用在最初那份替换脚本里漏了改绑。本机这两张表是空的 / 全为 NULL，
-- 所以一直没暴露；换到有数据的库上，改完 college/major 之后它们会指向已不存在的旧 id：
--   · category_similarity_threshold —— 按学院/专业配的相似度阈值
--     （阈值查不到不会报错，解析时会静默回退默认值）
--   · paper_submit.major_id
UPDATE category_similarity_threshold SET college_id = CASE college_id
  WHEN 1668572928001 THEN 1668573000001
  WHEN 1668572928003 THEN 1668573000004
  WHEN 1668572928005 THEN 1668573000007
  WHEN 1668572928007 THEN 1668573000011
  WHEN 1668572928008 THEN 1668573000012
  ELSE college_id END, update_by = @op, update_time = NOW()
WHERE college_id IN (1668572928001, 1668572928003, 1668572928005, 1668572928007, 1668572928008);

UPDATE category_similarity_threshold SET major_id = CASE major_id
  WHEN 1668572928015 THEN 1668573001004
  WHEN 1668572928026 THEN 1668573001020
  WHEN 1668572928045 THEN 1668573001065
  WHEN 1668572928049 THEN 1668573001068
  WHEN 1668572928051 THEN 1668573001070
  WHEN 1668572928054 THEN 1668573001069
  ELSE major_id END, update_by = @op, update_time = NOW()
WHERE major_id IN (1668572928015,1668572928026,1668572928045,1668572928049,1668572928051,1668572928054);

UPDATE paper_submit SET major_id = CASE major_id
  WHEN 1668572928015 THEN 1668573001004
  WHEN 1668572928026 THEN 1668573001020
  WHEN 1668572928045 THEN 1668573001065
  WHEN 1668572928049 THEN 1668573001068
  WHEN 1668572928051 THEN 1668573001070
  WHEN 1668572928054 THEN 1668573001069
  ELSE major_id END, update_by = @op, update_time = NOW()
WHERE major_id IN (1668572928015,1668572928026,1668572928045,1668572928049,1668572928051,1668572928054);

COMMIT;

SET FOREIGN_KEY_CHECKS = @fk;


-- ============================================================================
-- 段 3B：学科领域 subject + 专业↔学科映射 major_subject
--
-- 这 100 行（10 门类 + 90 二级）是原先 sys_dict_data 里 dict_type='subject_field'
-- 那批手工数据的固化 —— 它们此前只存在于库中，从未进过版本控制。这里按固定 id 重写，
-- 层级从「dict_value 里的连字符约定」改为 parent_id 关系。
--
-- 编码（code）与原 dict_value 完全一致，因此 paper_info.subject_code 的存量值一个都不用改，
-- 前端树选择器也不用改。
--
-- 整表替换语义，同段 1~3：DELETE 后按写死的固定 id 重插，可重复执行。
-- ⚠️ 替换 = 会删。管理端「学科领域管理」页自建的学科不在下面这份清单里，
--    跑本段会被 DELETE 掉、且不会回来（引用它的 paper_info.subject_id / 阈值行
--    随之悬空）。已经有自建学科的库不要跑本段；补数据请用 补*.sql 那一类文件，
--    或用 存量修复.sql 段 1.10 —— 那里是按 uk_subject_code upsert，不删行。
-- id 区间 1668573200000+（门类）/ 1668573201000+（二级）与 college 1668573000000+、
-- major 1668573001000+ 不重叠。
--
-- 专业映射是**多对多**：一个专业可对应多个学科（计算机科学与技术 → ENG-CS/ENG-SE/ENG-AI），
-- 一个学科也可被多个专业共用。没有映射的专业（哲学、历史学、体育类 4 个）在学科目录里
-- 确无对应项，故留空 —— 论文提交页对这类专业退回展示整棵树。
-- 映射是「学科目录 ↔ 专业目录」两套目录间的语义近似，不是严格等价。
-- ============================================================================

-- ---------------------------------------------------------------------------
-- 为什么这一段自带建表语句（本文件其它段都不带 DDL）
--
-- 本文件与 存量修复.sql 的约定执行顺序是「先 种子数据.sql、后 存量修复.sql」
-- （原因见 存量修复.sql 头部：段 1 要从本文件改绑过的 teacher_info / student_info
-- 回填 sys_user.major_id）。但 subject 这三张表是本次才新增的，落后的库上根本
-- 还没有 —— 段 3B 直接 INSERT 就会报 1146 Table 'subject' doesn't exist，而
-- 修复库结构的 1.10 段偏偏在它后面才跑。这里补上 IF NOT EXISTS 建表，让
-- 「先种子后修复」这个约定在落后库上照样成立，也省得操作者去记额外顺序。
-- 已有的库上是空操作；DDL 与 init.sql / 存量修复.sql 1.10.1 逐字一致。
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `subject` (
  `id` bigint NOT NULL COMMENT '主键ID（写死的13位，见 种子数据.sql 段 3B）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `parent_id` bigint DEFAULT NULL COMMENT '父学科ID（一级门类为NULL，二级指向所属门类id）',
  `level` tinyint NOT NULL DEFAULT '1' COMMENT '层级：1-一级门类，2-二级学科（由 parent_id 推导，不接受前端传值）',
  `code` varchar(50) NOT NULL COMMENT '学科编码（唯一，如 ENG、ENG-CS）。对外稳定标识，paper_info.subject_code 存它；创建后不可改',
  `name` varchar(128) NOT NULL COMMENT '学科名称（如 工学、人工智能），可改',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '同层排序',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用（0-停用，1-启用）：停用后不再出现在选择器/阈值候选中，已引用的数据不受影响',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_subject_code` (`code`),
  KEY `idx_subject_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='学科领域表（取代 sys_dict_data 的 subject_field 字符串树）';

CREATE TABLE IF NOT EXISTS `major_subject` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `major_id` bigint NOT NULL COMMENT '专业ID（关联major.id）',
  `subject_id` bigint NOT NULL COMMENT '学科领域ID（关联subject.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_major_subject` (`major_id`,`subject_id`),
  KEY `idx_major_subject_subject_id` (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='专业↔学科领域映射表';

CREATE TABLE IF NOT EXISTS `teacher_subject` (
  `id` bigint NOT NULL COMMENT '主键ID（雪花ID）',
  `create_by` bigint NOT NULL COMMENT '创建人ID（关联sys_user.id）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` bigint NOT NULL COMMENT '更新人ID（关联sys_user.id）',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint NOT NULL DEFAULT '0' COMMENT '软删除：0-未删除，1-已删除',
  `delete_by` bigint DEFAULT NULL COMMENT '删除人ID',
  `delete_time` datetime DEFAULT NULL COMMENT '删除时间',
  `user_id` bigint NOT NULL COMMENT '教师用户ID（关联sys_user.id）',
  `subject_id` bigint NOT NULL COMMENT '学科领域ID（关联subject.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_teacher_subject` (`user_id`,`subject_id`),
  KEY `idx_teacher_subject_subject_id` (`subject_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='教师研究方向↔学科领域关联表';

START TRANSACTION;

DELETE FROM `major_subject`;
DELETE FROM `subject`;

-- ---------- 1) 学科领域：10 个一级门类（parent_id = NULL） ----------
INSERT INTO `subject` (id, create_by, create_time, update_by, update_time, is_deleted, delete_by, delete_time, parent_id, level, code, name, sort_no, is_enabled) VALUES
(1668573200000, @op, NOW(), @op, NOW(), 0, NULL, NULL, NULL, 1, 'ENG',   '工学',     1, 1),
(1668573200001, @op, NOW(), @op, NOW(), 0, NULL, NULL, NULL, 1, 'MED',   '医学',     2, 1),
(1668573200002, @op, NOW(), @op, NOW(), 0, NULL, NULL, NULL, 1, 'SCI',   '理学',     3, 1),
(1668573200003, @op, NOW(), @op, NOW(), 0, NULL, NULL, NULL, 1, 'ECON',  '经济学',   4, 1),
(1668573200004, @op, NOW(), @op, NOW(), 0, NULL, NULL, NULL, 1, 'MGT',   '管理学',   5, 1),
(1668573200005, @op, NOW(), @op, NOW(), 0, NULL, NULL, NULL, 1, 'LAW',   '法学',     6, 1),
(1668573200006, @op, NOW(), @op, NOW(), 0, NULL, NULL, NULL, 1, 'LIT',   '文学',     7, 1),
(1668573200007, @op, NOW(), @op, NOW(), 0, NULL, NULL, NULL, 1, 'EDU',   '教育学',   8, 1),
(1668573200008, @op, NOW(), @op, NOW(), 0, NULL, NULL, NULL, 1, 'ART',   '艺术学',   9, 1),
(1668573200009, @op, NOW(), @op, NOW(), 0, NULL, NULL, NULL, 1, 'INTER', '交叉学科', 10, 1);

-- ---------- 2) 学科领域：90 个二级学科（parent_id 指向所属门类） ----------
INSERT INTO `subject` (id, create_by, create_time, update_by, update_time, is_deleted, delete_by, delete_time, parent_id, level, code, name, sort_no, is_enabled) VALUES
-- 工学 / ENG（24 个）
(1668573201000, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-AI',      '人工智能',               1, 1),
(1668573201001, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-CS',      '计算机科学与技术',       2, 1),
(1668573201002, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-SE',      '软件工程',               3, 1),
(1668573201003, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-CYBER',   '网络空间安全',           4, 1),
(1668573201004, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-BIGDATA', '大数据技术',             5, 1),
(1668573201005, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-CLOUD',   '云计算技术',             6, 1),
(1668573201006, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-IoT',     '物联网工程',             7, 1),
(1668573201007, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-BC',      '区块链工程',             8, 1),
(1668573201008, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-ME',      '微电子科学与工程',       9, 1),
(1668573201009, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-EE',      '电气工程及其自动化',    10, 1),
(1668573201010, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-AUTO',    '自动化',                11, 1),
(1668573201011, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-MECH',    '机械设计制造及其自动化', 12, 1),
(1668573201012, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-CIVIL',   '土木工程',              13, 1),
(1668573201013, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-HYDRO',   '水利水电工程',          14, 1),
(1668573201014, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-SURVEY',  '测绘工程',              15, 1),
(1668573201015, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-ENV',     '环境工程',              16, 1),
(1668573201016, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-CHEM',    '化学工程与工艺',        17, 1),
(1668573201017, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-FOOD',    '食品科学与工程',        18, 1),
(1668573201018, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-BIO',     '生物工程',              19, 1),
(1668573201019, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-AERO',    '航空航天工程',          20, 1),
(1668573201020, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-MARINE',  '海洋工程',              21, 1),
(1668573201021, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-RAIL',    '轨道交通信号与控制',    22, 1),
(1668573201022, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-COM',     '通信工程',              23, 1),
(1668573201023, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200000, 2, 'ENG-INFO',    '信息工程',              24, 1),
-- 医学 / MED（14 个）
(1668573201024, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-CLIN',   '临床医学',                1, 1),
(1668573201025, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-DENT',   '口腔医学',                2, 1),
(1668573201026, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-PH',     '公共卫生与预防医学',      3, 1),
(1668573201027, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-PHAR',   '药学',                    4, 1),
(1668573201028, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-TCM',    '中药学',                  5, 1),
(1668573201029, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-NURSE',  '护理学',                  6, 1),
(1668573201030, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-IMG',    '医学影像学',              7, 1),
(1668573201031, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-LAB',    '医学检验技术',            8, 1),
(1668573201032, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-REHAB',  '康复医学',                9, 1),
(1668573201033, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-PREV',   '预防医学',               10, 1),
(1668573201034, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-ANES',   '麻醉学',                 11, 1),
(1668573201035, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-PSY',    '精神病与精神卫生学',     12, 1),
(1668573201036, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-INT',    '中西医结合',             13, 1),
(1668573201037, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200001, 2, 'MED-PREC',   '精准医学',               14, 1),
-- 理学 / SCI（14 个）
(1668573201038, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-MATH',     '数学',          1, 1),
(1668573201039, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-PHY',      '物理学',        2, 1),
(1668573201040, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-CHEM',     '化学',          3, 1),
(1668573201041, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-BIO',      '生物学',        4, 1),
(1668573201042, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-ECOL',     '生态学',        5, 1),
(1668573201043, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-ASTR',     '天文学',        6, 1),
(1668573201044, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-GEO',      '地理学',        7, 1),
(1668573201045, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-ATM',      '大气科学',      8, 1),
(1668573201046, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-MAR',      '海洋科学',      9, 1),
(1668573201047, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-GEOP',     '地球物理学',   10, 1),
(1668573201048, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-GEOL',     '地质学',       11, 1),
(1668573201049, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-STAT',     '统计学',       12, 1),
(1668573201050, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-BIOINFO',  '生物信息学',   13, 1),
(1668573201051, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200002, 2, 'SCI-MATP',     '材料物理',     14, 1),
-- 经济学 / ECON（9 个）
(1668573201052, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-FIN',   '金融学',          1, 1),
(1668573201053, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-TRADE', '国际贸易学',      2, 1),
(1668573201054, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-FISC',  '财政学',          3, 1),
(1668573201055, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-ACC',   '会计学',          4, 1),
(1668573201056, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-BA',    '工商管理',        5, 1),
(1668573201057, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-THEO',  '经济学（理论）',  6, 1),
(1668573201058, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-DIG',   '数字经济',        7, 1),
(1668573201059, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-FE',    '金融工程',        8, 1),
(1668573201060, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200003, 2, 'ECON-INS',   '保险学',          9, 1),
-- 管理学 / MGT（9 个）
(1668573201061, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-PA',   '公共管理',           1, 1),
(1668573201062, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-LOG',  '物流管理',           2, 1),
(1668573201063, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-IMS',  '信息管理与信息系统', 3, 1),
(1668573201064, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-TOUR', '旅游管理',           4, 1),
(1668573201065, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-EM',   '工程管理',           5, 1),
(1668573201066, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-HR',   '人力资源管理',       6, 1),
(1668573201067, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-MKT',  '市场营销',           7, 1),
(1668573201068, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-FIN',  '财务管理',           8, 1),
(1668573201069, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200004, 2, 'MGT-EMER', '应急管理',           9, 1),
-- 法学 / LAW（6 个）
(1668573201070, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-THEO',  '法学理论',           1, 1),
(1668573201071, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-CIV',   '民商法学',           2, 1),
(1668573201072, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-CRIM',  '刑法学',             3, 1),
(1668573201073, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-ADMIN', '行政法学',           4, 1),
(1668573201074, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-INT',   '国际法学',           5, 1),
(1668573201075, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200005, 2, 'LAW-ENV',   '环境与资源保护法学', 6, 1),
-- 文学 / LIT（5 个）
(1668573201076, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200006, 2, 'LIT-CN', '中国语言文学', 1, 1),
(1668573201077, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200006, 2, 'LIT-EN', '外国语言文学', 2, 1),
(1668573201078, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200006, 2, 'LIT-JC', '新闻传播学',   3, 1),
(1668573201079, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200006, 2, 'LIT-AD', '广告学',       4, 1),
(1668573201080, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200006, 2, 'LIT-ED', '编辑出版学',   5, 1),
-- 教育学 / EDU（5 个）
(1668573201081, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200007, 2, 'EDU-THEO',   '教育学原理',     1, 1),
(1668573201082, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200007, 2, 'EDU-CUR',    '课程与教学论',   2, 1),
(1668573201083, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200007, 2, 'EDU-PRE',    '学前教育学',     3, 1),
(1668573201084, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200007, 2, 'EDU-HIGHER', '高等教育学',     4, 1),
(1668573201085, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200007, 2, 'EDU-VOC',    '职业技术教育学', 5, 1),
-- 艺术学 / ART（3 个）
(1668573201086, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200008, 2, 'ART-VIS', '视觉传达设计', 1, 1),
(1668573201087, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200008, 2, 'ART-ENV', '环境设计',     2, 1),
(1668573201088, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200008, 2, 'ART-DIG', '数字媒体艺术', 3, 1),
-- 交叉学科 / INTER（1 个）
(1668573201089, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573200009, 2, 'INTER-QIS', '量子信息科学', 1, 1);

-- ---------- 3) 专业↔学科映射（major_subject）----------
-- 只写有把握的映射；无对应项的专业（哲学、历史学、体育教育、社会体育指导与管理、
-- 运动人体科学、休闲体育）不在这里出现，提交页对它们退回展示整棵树。
INSERT INTO `major_subject` (id, create_by, create_time, update_by, update_time, is_deleted, delete_by, delete_time, major_id, subject_id) VALUES
-- 计算机科学与技术学院
(1668573300000, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001000, 1668573201001),  -- 计算机科学与技术 → ENG-CS
(1668573300001, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001001, 1668573201006),  -- 物联网工程 → ENG-IoT
(1668573300002, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001002, 1668573201000),  -- 智能科学与技术 → ENG-AI
(1668573300003, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001003, 1668573201004),  -- 数据科学与大数据技术 → ENG-BIGDATA
-- 软件学院
(1668573300004, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001004, 1668573201002),  -- 软件工程 → ENG-SE
(1668573300005, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001005, 1668573201022),  -- 网络工程 → ENG-COM
(1668573300006, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001005, 1668573201003),  -- 网络工程 → ENG-CYBER
(1668573300007, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001006, 1668573201088),  -- 数字媒体技术 → ART-DIG
-- 网络空间安全学院
(1668573300008, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001007, 1668573201003),  -- 信息安全 → ENG-CYBER
(1668573300009, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001008, 1668573201003),  -- 网络空间安全 → ENG-CYBER
(1668573300010, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001009, 1668573201003),  -- 保密技术 → ENG-CYBER
(1668573300011, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001010, 1668573201003),  -- 密码科学与技术 → ENG-CYBER
(1668573300012, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001010, 1668573201038),  -- 密码科学与技术 → SCI-MATH
-- 电子信息与电气工程学院
(1668573300013, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001011, 1668573201009),  -- 电气工程及其自动化 → ENG-EE
(1668573300014, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001012, 1668573201023),  -- 电子信息工程 → ENG-INFO
(1668573300015, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001012, 1668573201022),  -- 电子信息工程 → ENG-COM
(1668573300016, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001013, 1668573201008),  -- 电子科学与技术 → ENG-ME
(1668573300017, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001013, 1668573201023),  -- 电子科学与技术 → ENG-INFO
(1668573300018, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001014, 1668573201022),  -- 通信工程 → ENG-COM
(1668573300019, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001015, 1668573201008),  -- 微电子科学与工程 → ENG-ME
(1668573300020, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001016, 1668573201008),  -- 集成电路设计与集成系统 → ENG-ME
(1668573300021, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001017, 1668573201000),  -- 人工智能 → ENG-AI
-- 机械与动力工程学院
(1668573300022, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001018, 1668573201011),  -- 机械工程 → ENG-MECH
(1668573300023, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001019, 1668573201011),  -- 机械设计制造及其自动化 → ENG-MECH
(1668573300024, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001020, 1668573201011),  -- 机械电子工程 → ENG-MECH
(1668573300025, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001021, 1668573201011),  -- 车辆工程 → ENG-MECH
(1668573300026, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001022, 1668573201010),  -- 智能制造工程 → ENG-AUTO
(1668573300027, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001022, 1668573201011),  -- 智能制造工程 → ENG-MECH
(1668573300028, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001023, 1668573201011),  -- 新能源汽车工程 → ENG-MECH
(1668573300029, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001023, 1668573201009),  -- 新能源汽车工程 → ENG-EE
(1668573300030, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001024, 1668573201011),  -- 能源与动力工程 → ENG-MECH
-- 材料科学与工程学院
(1668573300031, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001025, 1668573201051),  -- 材料科学与工程 → SCI-MATP
(1668573300032, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001026, 1668573201051),  -- 材料物理 → SCI-MATP
(1668573300033, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001027, 1668573201051),  -- 金属材料工程 → SCI-MATP
(1668573300034, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001028, 1668573201051),  -- 高分子材料与工程 → SCI-MATP
(1668573300035, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001028, 1668573201016),  -- 高分子材料与工程 → ENG-CHEM
(1668573300036, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001029, 1668573201051),  -- 功能材料 → SCI-MATP
(1668573300037, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001030, 1668573201051),  -- 新能源材料与器件 → SCI-MATP
(1668573300038, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001030, 1668573201009),  -- 新能源材料与器件 → ENG-EE
-- 土木工程学院
(1668573300039, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001031, 1668573201012),  -- 土木工程 → ENG-CIVIL
(1668573300040, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001032, 1668573201012),  -- 建筑环境与能源应用工程 → ENG-CIVIL
(1668573300041, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001033, 1668573201012),  -- 给排水科学与工程 → ENG-CIVIL
(1668573300042, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001033, 1668573201013),  -- 给排水科学与工程 → ENG-HYDRO
(1668573300043, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001034, 1668573201012),  -- 道路桥梁与渡河工程 → ENG-CIVIL
(1668573300044, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001035, 1668573201012),  -- 智能建造 → ENG-CIVIL
(1668573300045, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001036, 1668573201012),  -- 建筑学 → ENG-CIVIL
(1668573300046, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001037, 1668573201065),  -- 工程管理 → MGT-EM
-- 化学与化工学院
(1668573300047, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001038, 1668573201040),  -- 化学 → SCI-CHEM
(1668573300048, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001039, 1668573201040),  -- 应用化学 → SCI-CHEM
(1668573300049, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001039, 1668573201016),  -- 应用化学 → ENG-CHEM
(1668573300050, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001040, 1668573201016),  -- 化学工程与工艺 → ENG-CHEM
(1668573300051, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001041, 1668573201027),  -- 制药工程 → MED-PHAR
(1668573300052, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001041, 1668573201016),  -- 制药工程 → ENG-CHEM
(1668573300053, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001042, 1668573201016),  -- 能源化学工程 → ENG-CHEM
(1668573300054, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001043, 1668573201016),  -- 精细化工 → ENG-CHEM
-- 生命科学学院
(1668573300055, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001044, 1668573201041),  -- 生物科学 → SCI-BIO
(1668573300056, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001045, 1668573201041),  -- 生物技术 → SCI-BIO
(1668573300057, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001045, 1668573201018),  -- 生物技术 → ENG-BIO
(1668573300058, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001046, 1668573201050),  -- 生物信息学 → SCI-BIOINFO
(1668573300059, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001047, 1668573201042),  -- 生态学 → SCI-ECOL
(1668573300060, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001048, 1668573201018),  -- 生物工程 → ENG-BIO
(1668573300061, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001049, 1668573201027),  -- 生物制药 → MED-PHAR
-- 数学与统计学院
(1668573300062, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001050, 1668573201038),  -- 数学与应用数学 → SCI-MATH
(1668573300063, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001051, 1668573201038),  -- 信息与计算科学 → SCI-MATH
(1668573300064, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001052, 1668573201038),  -- 数据计算及应用 → SCI-MATH
(1668573300065, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001052, 1668573201004),  -- 数据计算及应用 → ENG-BIGDATA
(1668573300066, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001053, 1668573201049),  -- 统计学 → SCI-STAT
(1668573300067, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001054, 1668573201049),  -- 应用统计学 → SCI-STAT
-- 物理与光电工程学院
(1668573300068, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001055, 1668573201039),  -- 物理学 → SCI-PHY
(1668573300069, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001056, 1668573201039),  -- 应用物理学 → SCI-PHY
(1668573300070, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001057, 1668573201039),  -- 核物理 → SCI-PHY
(1668573300071, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001058, 1668573201023),  -- 光电信息科学与工程 → ENG-INFO
(1668573300072, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001058, 1668573201039),  -- 光电信息科学与工程 → SCI-PHY
-- 经济与管理学院
(1668573300073, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001059, 1668573201057),  -- 经济学 → ECON-THEO
(1668573300074, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001060, 1668573201052),  -- 金融学 → ECON-FIN
(1668573300075, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001061, 1668573201059),  -- 金融工程 → ECON-FE
(1668573300076, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001062, 1668573201053),  -- 国际经济与贸易 → ECON-TRADE
(1668573300077, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001063, 1668573201063),  -- 信息管理与信息系统 → MGT-IMS
(1668573300078, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001064, 1668573201004),  -- 大数据管理与应用 → ENG-BIGDATA
(1668573300079, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001064, 1668573201063),  -- 大数据管理与应用 → MGT-IMS
(1668573300080, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001065, 1668573201056),  -- 工商管理 → ECON-BA
(1668573300081, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001066, 1668573201055),  -- 会计学 → ECON-ACC
(1668573300082, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001067, 1668573201068),  -- 财务管理 → MGT-FIN
(1668573300083, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001068, 1668573201066),  -- 人力资源管理 → MGT-HR
-- 法学院
(1668573300084, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001069, 1668573200005),  -- 法学 → LAW（门类）
(1668573300085, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001070, 1668573201071),  -- 知识产权 → LAW-CIV
(1668573300086, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001071, 1668573201074),  -- 国际经贸规则 → LAW-INT
(1668573300087, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001071, 1668573201053),  -- 国际经贸规则 → ECON-TRADE
(1668573300088, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001072, 1668573201073),  -- 纪检监察 → LAW-ADMIN
(1668573300089, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001072, 1668573201072),  -- 纪检监察 → LAW-CRIM
-- 外国语学院（6 个专业同归外国语言文学）
(1668573300090, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001073, 1668573201077),  -- 英语 → LIT-EN
(1668573300091, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001074, 1668573201077),  -- 德语 → LIT-EN
(1668573300092, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001075, 1668573201077),  -- 法语 → LIT-EN
(1668573300093, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001076, 1668573201077),  -- 日语 → LIT-EN
(1668573300094, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001077, 1668573201077),  -- 翻译 → LIT-EN
(1668573300095, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001078, 1668573201077),  -- 商务英语 → LIT-EN
-- 文学院
(1668573300096, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001080, 1668573201076),  -- 汉语言文学 → LIT-CN
(1668573300097, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001081, 1668573201076),  -- 汉语国际教育 → LIT-CN
(1668573300098, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001082, 1668573201076),  -- 秘书学 → LIT-CN
-- 新闻与传播学院
(1668573300099, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001084, 1668573201078),  -- 新闻学 → LIT-JC
(1668573300100, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001085, 1668573201078),  -- 广播电视学 → LIT-JC
(1668573300101, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001086, 1668573201079),  -- 广告学 → LIT-AD
(1668573300102, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001087, 1668573201078),  -- 网络与新媒体 → LIT-JC
(1668573300103, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001088, 1668573201078),  -- 国际新闻与传播 → LIT-JC
(1668573300104, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001088, 1668573201077),  -- 国际新闻与传播 → LIT-EN
-- 艺术与设计学院
(1668573300105, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001089, 1668573200008),  -- 音乐学 → ART（门类）
(1668573300106, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001090, 1668573201088),  -- 动画 → ART-DIG
(1668573300107, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001091, 1668573200008),  -- 美术学 → ART（门类）
(1668573300108, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001092, 1668573201086),  -- 视觉传达设计 → ART-VIS
(1668573300109, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001093, 1668573201087),  -- 环境设计 → ART-ENV
(1668573300110, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001094, 1668573201086),  -- 产品设计 → ART-VIS
(1668573300111, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001095, 1668573201088),  -- 数字媒体艺术 → ART-DIG
-- 医学院
(1668573300112, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001096, 1668573200001),  -- 基础医学 → MED（门类）
(1668573300113, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001097, 1668573201024),  -- 临床医学 → MED-CLIN
(1668573300114, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001098, 1668573201025),  -- 口腔医学 → MED-DENT
(1668573300115, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001099, 1668573201033),  -- 预防医学 → MED-PREV
(1668573300116, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001100, 1668573201027),  -- 药学 → MED-PHAR
(1668573300117, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001101, 1668573201028),  -- 中药学 → MED-TCM
(1668573300118, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001102, 1668573201032),  -- 康复治疗学 → MED-REHAB
(1668573300119, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001103, 1668573201029),  -- 护理学 → MED-NURSE
-- 环境与资源学院
(1668573300120, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001104, 1668573201044),  -- 人文地理与城乡规划 → SCI-GEO
(1668573300121, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001105, 1668573201044),  -- 地理信息科学 → SCI-GEO
(1668573300122, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001106, 1668573201048),  -- 资源勘查工程 → SCI-GEOL
(1668573300123, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001107, 1668573201015),  -- 环境工程 → ENG-ENV
(1668573300124, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001108, 1668573201015),  -- 环境科学 → ENG-ENV
(1668573300125, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001108, 1668573201042),  -- 环境科学 → SCI-ECOL
(1668573300126, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001109, 1668573201042),  -- 环境生态工程 → SCI-ECOL
(1668573300127, @op, NOW(), @op, NOW(), 0, NULL, NULL, 1668573001109, 1668573201015);  -- 环境生态工程 → ENG-ENV

COMMIT;


-- ============================================================================
-- 段 4：操作类型字典 sys_operation_type
--
-- 日志中心「模块使用率」饼图靠
--   LEFT JOIN sys_operation_type t ON t.type = l.operation_type
-- 取 module，未收录的 type 会落到 COALESCE 的「未知」。这张表由 SQL 维护、
-- 没有任何代码往它插数据，所以这份数据是唯一来源。
--
-- 幂等方式：按 type 上的唯一键 uk_type 做 ON DUPLICATE KEY UPDATE。
-- ============================================================================

INSERT INTO `sys_operation_type` (`id`, `type`, `name`, `description`, `module`, `permission_level`, `status`, `create_time`, `update_time`) VALUES 
(1,'user_login','用户登录','用户登录系统','用户管理','LOW',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(2,'user_logout','用户登出','用户退出系统','用户管理','LOW',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(3,'user_register','用户注册','用户注册账号','用户管理','LOW',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(4,'user_update','更新用户信息','更新用户基本信息','用户管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(5,'user_password_change','修改密码','修改用户密码','用户管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(6,'user_role_assign','分配用户角色','为用户分配角色权限','用户管理','HIGH',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(7,'paper_submit','提交论文','学生提交论文','论文管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(8,'paper_update','更新论文','更新论文信息','论文管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(9,'paper_delete','删除论文','删除论文记录','论文管理','HIGH',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(10,'paper_review','论文审核','审核学生论文','论文管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(11,'paper_approve','论文批准','批准学生论文','论文管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(12,'paper_reject','论文拒绝','拒绝学生论文','论文管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(13,'task_create','创建查重任务','创建论文查重任务','查重管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(14,'task_submit','提交查重任务','提交论文查重任务','查重管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(15,'task_cancel','取消查重任务','取消未完成的查重任务','查重管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(16,'task_delete','删除查重任务','删除查重任务记录','查重管理','HIGH',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(17,'task_result_view','查看查重结果','查看论文查重结果','查重管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(18,'task_result_export','导出查重结果','导出论文查重结果','查重管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(19,'teacher_assign','分配导师','为学生分配导师','教师管理','HIGH',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(20,'teacher_update','更新教师信息','更新教师基本信息','教师管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(21,'teacher_student_manage','管理学生','教师管理学生信息','教师管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(22,'teacher_review_assign','分配评审任务','分配论文评审任务','教师管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(23,'teacher_review_complete','完成评审任务','完成论文评审任务','教师管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(24,'system_config_update','更新系统配置','更新系统基础配置','系统管理','HIGH',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(25,'system_param_update','更新系统参数','更新系统运行参数','系统管理','HIGH',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(26,'dict_data_manage','管理字典数据','管理系统字典数据','系统管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(27,'permission_manage','管理权限','管理系统权限','系统管理','HIGH',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(28,'role_manage','管理角色','管理系统角色','系统管理','HIGH',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(29,'notice_send','发送通知','发送系统通知','系统管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(30,'student_info_update','更新学生信息','更新学生基本信息','学生管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(31,'student_paper_manage','管理学生论文','管理学生论文信息','学生管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(32,'student_task_view','查看学生任务','查看学生任务信息','学生管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(33,'student_submission_manage','管理学生提交','管理学生提交记录','学生管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(34,'notification_send','发送通知','发送系统通知','通知管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(35,'notification_update','更新通知','更新通知信息','通知管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(36,'notification_delete','删除通知','删除通知记录','通知管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(37,'notification_view','查看通知','查看通知信息','通知管理','LOW',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(38,'log_export','导出日志','导出系统日志','日志管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(39,'log_view','查看日志','查看系统日志','日志管理','MEDIUM',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(40,'log_clean','清理日志','清理系统日志','日志管理','HIGH',1,'2026-04-27 21:06:14','2026-04-27 21:06:14'),
(41,'user_delete','删除用户','删除用户账号','用户管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(42,'user_batch_delete','批量删除用户','批量删除用户账号','用户管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(43,'user_status_update','更新用户状态','更新用户账号状态','用户管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(44,'user_upload_avatar','上传头像','上传用户头像','用户管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(45,'user_verify_email','验证邮箱','验证用户邮箱地址','用户管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(46,'user_send_verify_email','发送邮箱验证邮件','发送邮箱验证邮件到用户邮箱','用户管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(47,'user_send_email_code','发送邮箱验证码','发送邮箱验证码到用户邮箱','用户管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(48,'user_update_email','更新用户邮箱','更新用户邮箱地址','用户管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(49,'user_login_history','查询登录历史','查询用户登录历史记录','用户管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(50,'paper_batch_audit','批量审核论文','批量审核学生论文','论文管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(51,'paper_resubmit','重新提交论文','学生重新提交论文','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(52,'paper_withdraw','撤回论文','学生撤回已提交的论文','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(53,'paper_modify_request','申请修改论文','学生申请修改论文','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(54,'paper_batch_download','批量下载论文','批量下载论文文件','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(55,'paper_batch_delete','批量删除论文','批量删除论文记录','论文管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(56,'attachment_upload','上传附件','上传论文附件','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(57,'attachment_delete','删除附件','删除论文附件','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(58,'task_batch_create','批量创建查重任务','批量创建论文查重任务','查重管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(59,'check_task_list','查询查重任务列表','查询查重任务列表','查重管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(60,'check_task_detail','查询查重任务详情','查询查重任务详情','查重管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(61,'check_task_by_id','查询指定任务详情','根据ID查询查重任务详情','查重管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(62,'check_task_status','获取查重任务状态','获取查重任务状态','查重管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(63,'check_task_recheck','重新发起查重','重新发起论文查重','查重管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(64,'check_rule_create','创建查重规则','创建论文查重规则','查重管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(65,'check_rule_update','更新查重规则','更新论文查重规则','查重管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(66,'check_rule_delete','删除查重规则','删除论文查重规则','查重管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(67,'teacher_get_info','获取教师信息','获取教师个人信息','教师管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(68,'contact_student','联系学生','教师联系学生','教师管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(69,'delegate_review','委托审核','委托其他教师审核','教师管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(70,'delete_student','删除学生','删除学生账号','教师管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(71,'batch_assign_advisor','批量分配导师','批量为学生分配导师','教师管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(72,'batch_send_message','批量发送消息','批量向学生发送消息','教师管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(73,'batch_delete_student','批量删除学生','批量删除学生账号','教师管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(74,'export_students','导出学生数据','导出学生信息到文件','教师管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(75,'add_student','添加学生','添加学生账号','教师管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(76,'import_students','导入学生数据','从文件导入学生信息','教师管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(77,'send_message','发送消息','向用户发送消息','教师管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(78,'assignment_create','创建分配任务','创建分配任务记录','教师管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(79,'assignment_update','更新分配任务','更新分配任务信息','教师管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(80,'assignment_delete','删除分配任务','删除分配任务记录','教师管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(81,'teacher_allocation_create','创建教师分配记录','创建教师分配记录','教师管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(82,'teacher_allocation_revoke','撤销教师分配记录','撤销已分配的教师','教师管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(83,'teacher_allocation_batch_create','批量创建教师分配记录','批量创建教师分配记录','教师管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(84,'teacher_assign_cancel','取消导师分配','取消已分配的导师','教师管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(85,'batch_teacher_assign','批量分配导师','批量为学生分配导师','教师管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(86,'auto_allocation_create','创建自动分配记录','创建自动分配历史记录','教师管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(87,'config_get_all','获取所有配置','获取系统所有配置信息','系统管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(88,'config_basic_update','更新基础配置','更新系统基础配置','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(89,'config_plagiarism_update','更新查重配置','更新查重相关配置','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(90,'config_security_update','更新安全配置','更新系统安全配置','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(91,'config_email_update','更新邮件配置','更新邮件发送配置','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(92,'config_performance_update','更新性能配置','更新系统性能配置','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(93,'config_save_all','保存全部配置','保存所有系统配置','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(94,'config_test_email','测试邮件配置','测试邮件发送配置','系统管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(95,'config_export','导出配置','导出系统配置到文件','系统管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(96,'config_reset_default','恢复默认配置','将系统配置恢复为默认值','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(97,'config_refresh','刷新配置','刷新系统配置缓存','系统管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(98,'role_list','获取角色列表','获取系统角色列表','系统管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(99,'role_create','创建角色','创建系统角色','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(100,'role_update','更新角色','更新系统角色','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(101,'role_delete','删除角色','删除系统角色','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(102,'permission_tree','获取权限树','获取系统权限树','系统管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(103,'user_assign_role','分配用户角色','为用户分配系统角色','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(104,'system_config_reset','重置系统配置','将系统配置恢复为默认值','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(105,'system_param_init','初始化系统参数','初始化系统运行参数','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(106,'system_maintenance','系统维护设置','设置系统维护状态','系统管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(107,'notification_template_create','创建通知模板','创建消息通知模板','通知管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(108,'notification_template_status','切换通知模板状态','启用或禁用通知模板','通知管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(109,'notification_read','标记已读','标记通知为已读','通知管理','LOW',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(110,'batch_send_notification','批量发送通知','批量向用户发送通知','通知管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(111,'message_template_create','创建消息模板','创建消息通知模板','通知管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(112,'message_template_update','更新消息模板','更新消息通知模板','通知管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(113,'message_template_delete','删除消息模板','删除消息通知模板','通知管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(114,'message_template_status','切换消息模板状态','启用或禁用消息模板','通知管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(115,'admin_user_create','管理员创建用户','管理员创建用户账号','用户管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(116,'admin_user_update','管理员更新用户信息','管理员更新用户信息','用户管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(117,'admin_user_delete','管理员删除用户','管理员删除用户账号','用户管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(118,'admin_user_batch_delete','管理员批量删除用户','管理员批量删除用户账号','用户管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(119,'admin_user_status_update','管理员更新用户状态','管理员更新用户账号状态','用户管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(120,'admin_user_password_reset','管理员重置用户密码','管理员重置用户密码','用户管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(121,'admin_paper_audit','管理员审核论文','管理员审核学生论文','论文管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(122,'admin_paper_batch_audit','管理员批量审核论文','管理员批量审核学生论文','论文管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(123,'admin_paper_delete','管理员删除论文','管理员删除论文记录','论文管理','HIGH',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(124,'student_paper_submit','学生提交论文','学生提交论文','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(125,'student_paper_update','学生更新论文信息','学生更新论文信息','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(126,'student_paper_resubmit','撤回后重新提交','撤回后重新提交论文','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(127,'student_paper_withdraw','学生撤回论文','学生撤回已提交的论文','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(128,'student_paper_modify_request','学生申请修改论文','学生申请修改论文','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(129,'student_paper_batch_download','学生批量下载论文','学生批量下载论文文件','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(130,'student_paper_batch_delete','学生批量删除论文','学生批量删除论文记录','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(131,'student_attachment_upload','学生上传论文附件','学生上传论文附件','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(132,'student_attachment_delete','学生删除论文附件','学生删除论文附件','论文管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(133,'recheck_plagiarism','重新查重检测','重新进行论文查重检测','查重管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(134,'send_reminder','发送提醒消息','向用户发送提醒消息','通知管理','MEDIUM',1,'2026-04-27 21:26:09','2026-04-27 21:26:09'),
(135,'admin_auto_assign','管理员自动分配',NULL,'教师管理','HIGH',1,'2026-10-03 14:21:01','2026-10-03 14:21:01'),
(136,'check_task_create','创建查重任务',NULL,'查重管理','MEDIUM',1,'2026-10-03 14:21:01','2026-10-03 14:21:01'),
(137,'check_task_delete','删除查重任务',NULL,'查重管理','HIGH',1,'2026-10-03 14:21:01','2026-10-03 14:21:01'),
(138,'user_update_info','更新用户信息',NULL,'用户管理','MEDIUM',1,'2026-10-03 14:21:01','2026-10-03 14:21:01'),
(139,'teacher_update_info','更新教师信息',NULL,'教师管理','MEDIUM',1,'2026-10-03 14:21:01','2026-10-03 14:21:01'),
-- 以下 58 条为 2026-10-18 补录：全项目 @OperationLog 共 100 个 type，此前字典仅 139 条
-- （覆盖 42 个），P1~P5 新增的答辩/评阅/性能配置/知识库/通知等操作 type 全部缺失，
-- 日志中心「模块使用率」LEFT JOIN 不上后统一落 COALESCE 的「未知」（2026-10-17 实测
-- 返回单行 {moduleName:未知, count:58}）。补录后 log_count_types 自检期望 197。
(140,'admin_advisor_eval_export','管理员导出导师评价明细','管理员导出导师评价明细','教师管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(141,'admin_backup_settings_update','管理员更新备份设置','管理员更新备份设置','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(142,'admin_backup_trigger','管理员手动触发数据库备份','管理员手动触发数据库备份','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(143,'admin_check_quota_save','管理员保存学院月度查重额度','管理员保存学院月度查重额度','查重管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(144,'admin_config_performance_reset','管理员重置性能配置为默认值','管理员重置性能配置为默认值','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(145,'admin_config_performance_test','管理员测试应用性能配置','管理员测试应用性能配置','系统管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(146,'admin_config_performance_update','管理员更新性能配置','管理员更新性能配置','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(147,'admin_defense_batch_create','管理员新建答辩批次','管理员新建答辩批次','答辩管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(148,'admin_defense_batch_delete','管理员删除答辩批次','管理员删除答辩批次','答辩管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(149,'admin_defense_batch_update','管理员编辑答辩批次','管理员编辑答辩批次','答辩管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(150,'admin_defense_enroll','管理员安排学生入答辩批次','管理员安排学生入答辩批次','答辩管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(151,'admin_defense_members_assign','管理员分配答辩组成员','管理员分配答辩组成员','答辩管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(152,'admin_manual_assign','管理员手动分配指导老师','管理员手动分配指导老师','教师管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(153,'admin_review_assign_allocate','管理员分配评阅任务','管理员分配评阅任务','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(154,'admin_role_create','管理员创建角色','管理员创建角色','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(155,'admin_role_delete','管理员删除角色','管理员删除角色','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(156,'admin_role_update','管理员更新角色','管理员更新角色','系统管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(157,'admin_thesis_score_recompute','管理员重算论文总评成绩','管理员重算论文总评成绩','论文管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(158,'admin_user_role_assign','管理员分配用户角色','管理员分配用户角色','用户管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(159,'assign_advisor','分配导师','分配导师','教师管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(160,'check_policy_delete','删除检测策略','删除检测策略','查重管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(161,'check_policy_save','保存检测策略','保存检测策略','查重管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(162,'config_email_test','管理员发送测试邮件','管理员发送测试邮件','系统管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(163,'knowledge_article_create','新增知识库文章','新增知识库文章','知识库','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(164,'knowledge_article_delete','删除知识库文章','删除知识库文章','知识库','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(165,'knowledge_article_status_update','更新知识库文章状态','更新知识库文章状态','知识库','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(166,'knowledge_article_update','更新知识库文章','更新知识库文章','知识库','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(167,'message_template_status_update','更新消息模板启用状态','更新消息模板启用状态','系统管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(168,'notice_batch_delete','批量删除通知','批量删除通知','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(169,'notice_batch_read','批量标记通知已读','批量标记通知已读','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(170,'notice_clear_all','清空通知','清空通知','通知管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(171,'notice_delete','删除通知','删除通知','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(172,'notice_read','标记通知已读','标记通知已读','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(173,'notice_read_all','全部标记通知已读','全部标记通知已读','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(174,'notification_paper_review','发送论文审核结果通知','发送论文审核结果通知','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(175,'notification_paper_status','发送论文状态变更通知','发送论文状态变更通知','通知管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(176,'review_workflow_update','更新审核工作流配置','更新审核工作流配置','论文管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(177,'similarity_threshold_update','更新相似度阈值设置','更新相似度阈值设置','查重管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(178,'student_advisor_eval_submit','学生提交导师评价','学生提交导师评价','学生管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(179,'student_group_add_students','添加学生到分组','添加学生到分组','学生管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(180,'student_group_create','创建学生分组','创建学生分组','学生管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(181,'student_group_delete','删除学生分组','删除学生分组','学生管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(182,'student_group_remove_student','从分组移除学生','从分组移除学生','学生管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(183,'student_group_update','更新学生分组','更新学生分组','学生管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(184,'student_guidance_confirm','学生确认指导记录','学生确认指导记录','学生管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(185,'subject_create','新增学科领域','新增学科领域','学科管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(186,'subject_delete','删除学科领域','删除学科领域','学科管理','HIGH',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(187,'subject_enabled','启用或停用学科领域','启用或停用学科领域','学科管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(188,'subject_update','修改学科领域','修改学科领域','学科管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(189,'teacher_batch_review','教师批量审核论文','教师批量审核论文','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(190,'teacher_check_signoff','教师签核查重报告','教师签核查重报告','查重管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(191,'teacher_confirm_assignment','教师确认论文分配','教师确认论文分配','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(192,'teacher_defense_score_submit','答辩组长提交评分结论','答辩组长提交评分结论','答辩管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(193,'teacher_guidance_create','教师发起指导记录','教师发起指导记录','教师管理','LOW',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(194,'teacher_paper_review','教师审核论文','教师审核论文','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(195,'teacher_reject_assignment','教师拒绝论文分配','教师拒绝论文分配','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(196,'teacher_review_assign_submit','教师提交评阅结果','教师提交评阅结果','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00'),
(197,'teacher_workflow_vote','教师多级初审投票','教师多级初审投票','教师管理','MEDIUM',1,'2026-10-18 00:00:00','2026-10-18 00:00:00')
ON DUPLICATE KEY UPDATE `type` = VALUES(`type`), `name` = VALUES(`name`), `description` = VALUES(`description`), `module` = VALUES(`module`), `permission_level` = VALUES(`permission_level`), `status` = VALUES(`status`);


-- ============================================================================
-- 段 5：知识库分类 knowledge_category
--
-- knowledge_article.category_id 指向本表的 id，所以要带上真实 id 插。
-- 没有任何代码按固定的 code 查分类（KnowledgeCategoryServiceImpl.getByCode 的
-- 入参由请求带过来），因此这里只需要保住「文章引用得到的那几行」。
--
-- 幂等方式：按 code 上的唯一键做 ON DUPLICATE KEY UPDATE（id 不参与更新）。
-- ============================================================================

INSERT INTO `knowledge_category` (`id`, `code`, `name`, `icon`, `sort`, `status`, `create_time`, `update_time`, `is_deleted`) VALUES 
(707990676951412736,'beginner_guide','新手指南','User',0,'ACTIVE','2026-05-09 00:22:11','2026-05-09 00:22:11',0),
(707990676968189952,'submit_guide','论文提交','Upload',0,'ACTIVE','2026-05-09 00:22:11','2026-05-09 00:22:11',0),
(707990676972384256,'check_guide','查重说明','Search',0,'ACTIVE','2026-05-09 00:22:11','2026-05-09 00:22:11',0),
(707990676976578560,'review_guide','审核流程','Document',0,'ACTIVE','2026-05-09 00:22:11','2026-05-09 00:22:11',0),
(707990676984967168,'faq','常见问题','QuestionFilled',0,'ACTIVE','2026-05-09 00:22:11','2026-05-09 00:22:11',0),
(707990676997550080,'announcement','通知公告','Bell',0,'ACTIVE','2026-05-09 00:22:11','2026-05-09 00:22:11',0)
ON DUPLICATE KEY UPDATE `name` = VALUES(`name`), `icon` = VALUES(`icon`), `sort` = VALUES(`sort`), `status` = VALUES(`status`), `is_deleted` = 0;


-- ============================================================================
-- 段 5B：帮助中心文章 knowledge_article（15 篇）
--
-- 段 5 只建了 6 个分类，本段播正文 —— 缺它的话，帮助中心只有空分类。
-- 文章靠 category_id 指向段 5 的分类 id，所以段 5 必须先跑（本文件顺序已保证）。
--
-- 用 INSERT IGNORE 而不是 ON DUPLICATE KEY UPDATE，三个理由：
--   1) 空库跑就是全插，本段的目标场景；
--   2) 重复跑是无操作，不会报错；
--   3) 永远不会覆盖管理端在界面上改过的文章 —— 种子数据不该反向回写用户内容。
--
-- id 是写死的雪花 id。正文是 **Markdown**（全 15 篇都没有 HTML 标签，前端按
-- Markdown 渲染）；summary 是正文开头截的一段，列表页用。status='PUBLISHED'
-- 才在前台可见。author_id=0 / author_name='系统管理员'：0 在 sys_user 里没有
-- 对应行，该列无外键，只用于展示作者名，别去补用户。
-- （原 补知识库.sql 内容归并进本段，2026-10-12）
-- ============================================================================

INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677043687424,'系统概述与快速入门','## 系统概述\n\n论文查重管理系统是一站式毕业论文管理平台，支持论文提交、查重检测、导师审核全流程。\n\n### 主要功能\n- **论文提交**：上传 DOC/DOCX/PDF 格式论文\n- **查重检测**：基于 SimHash 算法的本地查重引擎\n- **导师互动**：与指导老师实时沟通\n- **审核流程**：教师在线审核并给出修改意见\n\n### 快速上手\n1. 注册账号并完善个人信息\n2. 确认导师分配\n3. 提交论文文件\n4. 等待查重结果\n5. 根据查重报告修改论文\n','## 系统概述\n\n论文查重管理系统是一站式毕业论文管理平台，支持论文提交、查重检测、导师审核全流程。\n\n### 主要功能\n- **论文提交**：上传 DOC/DOCX/PDF 格式论文\n- **查重检测**：基于 SimHash 算法的本地查重引擎\n- **导师互动**：与指导老师实时沟通\n- **审核流程**：教师在线审核并给出修改意见\n\n### 快速上手\n1. 注册账号并完善个人信息\n2. 确',707990676951412736,'',1,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-26 19:35:54',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677064658944,'如何注册账号','## 注册流程\n\n1. 访问系统首页，点击\"注册\"按钮\n2. 填写账号信息：学号、姓名、身份证号\n3. 选择学院、专业、年级\n4. 设置密码\n5. 提交注册，等待审核\n\n> 注：学号和身份证号将用于身份验证，请如实填写。\n','## 注册流程\n\n1. 访问系统首页，点击\"注册\"按钮\n2. 填写账号信息：学号、姓名、身份证号\n3. 选择学院、专业、年级\n4. 设置密码\n5. 提交注册，等待审核\n\n> 注：学号和身份证号将用于身份验证，请如实填写。\n',707990676951412736,'',2,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-10 13:16:50',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677068853248,'个人信息设置指南','## 完善个人信息\n\n登录后，点击右上角头像 → \"账号设置\"：\n\n- **基本信息**：姓名、学号、学院、专业、班级\n- **联系方式**：邮箱（用于接收查重通知）、手机号\n- **头像**：支持上传自定义头像\n- **密码修改**：定期更换密码保障账户安全\n\n信息填写完整后，系统将根据您的专业自动匹配合适的导师。\n','## 完善个人信息\n\n登录后，点击右上角头像 → \"账号设置\"：\n\n- **基本信息**：姓名、学号、学院、专业、班级\n- **联系方式**：邮箱（用于接收查重通知）、手机号\n- **头像**：支持上传自定义头像\n- **密码修改**：定期更换密码保障账户安全\n\n信息填写完整后，系统将根据您的专业自动匹配合适的导师。\n',707990676951412736,'',2,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-06-21 19:33:03',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677077241856,'论文提交流程详解','## 论文提交步骤\n\n### 第一步：准备文件\n支持格式：`.doc` / `.docx` / `.pdf`，单个文件不超过 200MB\n\n### 第二步：上传论文\n进入\"论文提交\"页面，拖拽或点击上传论文文件\n\n### 第三步：填写论文信息\n- 论文标题（必填）\n- 摘要（必填）\n- 关键词（选填）\n- 指导教师（已自动匹配）\n\n### 第四步：确认提交\n检查信息无误后点击\"提交\"，系统将自动开始查重流程。\n','## 论文提交步骤\n\n### 第一步：准备文件\n支持格式：`.doc` / `.docx` / `.pdf`，单个文件不超过 200MB\n\n### 第二步：上传论文\n进入\"论文提交\"页面，拖拽或点击上传论文文件\n\n### 第三步：填写论文信息\n- 论文标题（必填）\n- 摘要（必填）\n- 关键词（选填）\n- 指导教师（已自动匹配）\n\n### 第四步：确认提交\n检查信息无误后点击\"提交\"，系统将自动开',707990676968189952,'',0,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-09 00:22:11',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677081436160,'文件格式与大小要求','## 文件规范\n\n| 项目 | 要求 |\n|------|------|\n| 支持格式 | .doc, .docx, .pdf |\n| 最大体积 | 200MB |\n| 页数限制 | 无限制 |\n| 加密文件 | 不支持 |\n\n### 注意事项\n- PDF 文件需为可编辑文本型（扫描版需 OCR 处理）\n- 文件中有图片、公式不影响查重\n- 文件名建议使用\"学号_姓名_论文标题\"格式\n','## 文件规范\n\n| 项目 | 要求 |\n|------|------|\n| 支持格式 | .doc, .docx, .pdf |\n| 最大体积 | 200MB |\n| 页数限制 | 无限制 |\n| 加密文件 | 不支持 |\n\n### 注意事项\n- PDF 文件需为可编辑文本型（扫描版需 OCR 处理）\n- 文件中有图片、公式不影响查重\n- 文件名建议使用\"学号_姓名_论文标题\"格式\n',707990676968189952,'',1,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-09 22:14:28',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677085630464,'查重原理与算法说明','## 查重原理\n\n系统采用 **SimHash 局部敏感哈希算法** 进行文本相似度检测。\n\n### 检测流程\n1. **文本提取**：使用 Apache Tika 提取论文正文\n2. **中文分词**：IK Analyzer 分词器进行中文分词\n3. **SimHash 计算**：生成 64 位指纹\n4. **海明距离比较**：与对比库中论文逐一比较\n5. **相似度计算**：海明距离 ≤ 3 判定为相似\n\n### 相似度阈值\n- 默认阈值：20%\n- 超过阈值将触发相似度预警\n- 教师可根据需要调整阈值\n','## 查重原理\n\n系统采用 **SimHash 局部敏感哈希算法** 进行文本相似度检测。\n\n### 检测流程\n1. **文本提取**：使用 Apache Tika 提取论文正文\n2. **中文分词**：IK Analyzer 分词器进行中文分词\n3. **SimHash 计算**：生成 64 位指纹\n4. **海明距离比较**：与对比库中论文逐一比较\n5. **相似度计算**：海明距离 ≤ 3 ',707990676972384256,'',0,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-09 00:22:11',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677098213376,'查重报告解读','## 如何读懂查重报告\n\n### 报告内容\n- **总相似度**：论文整体重复率\n- **章节相似度**：各章节分别统计\n- **相似来源**：匹配到的参考文献及相似段落\n- **相似度分布图**：可视化展示重复部分\n\n### 相似度等级\n| 等级 | 范围 | 说明 |\n|------|------|------|\n| 绿色 | 0-20% | 正常范围，可以提交审核 |\n| 黄色 | 20-40% | 偏高，建议修改后重查 |\n| 红色 | 40%+ | 严重，需要大幅修改 |\n','## 如何读懂查重报告\n\n### 报告内容\n- **总相似度**：论文整体重复率\n- **章节相似度**：各章节分别统计\n- **相似来源**：匹配到的参考文献及相似段落\n- **相似度分布图**：可视化展示重复部分\n\n### 相似度等级\n| 等级 | 范围 | 说明 |\n|------|------|------|\n| 绿色 | 0-20% | 正常范围，可以提交审核 |\n| 黄色 | 20-4',707990676972384256,'',0,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-09 00:22:11',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677102407680,'论文审核流程说明','## 审核流程\n\n```mermaid\ngraph LR\n    A[提交论文] --> B[自动查重]\n    B --> C[导师审核]\n    C --> D{审核结果}\n    D -->|通过| E[完成]\n    D -->|需修改| F[退回修改]\n    F --> A\n```\n\n### 各环节说明\n1. **自动查重**：系统自动完成，约 1-5 分钟\n2. **导师审核**：导师阅读论文和查重报告，给出审核意见\n3. **审核结果**：\n   - 通过：论文合格\n   - 需修改：根据导师意见修改后重新提交\n   - 退回：严重问题需重新撰写\n','## 审核流程\n\n```mermaid\ngraph LR\n    A[提交论文] --> B[自动查重]\n    B --> C[导师审核]\n    C --> D{审核结果}\n    D -->|通过| E[完成]\n    D -->|需修改| F[退回修改]\n    F --> A\n```\n\n### 各环节说明\n1. **自动查重**：系统自动完成，约 1-5 分钟\n2. **导师审核**：导',707990676976578560,'',0,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-09 22:11:47',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677106601984,'审核状态说明','## 审核状态一览\n\n| 状态 | 图标 | 说明 |\n|------|------|------|\n| 待提交 | ⏳ | 尚未提交论文 |\n| 待分配 | 📋 | 等待分配导师 |\n| 已分配 | 👤 | 导师已分配 |\n| 查重中 | 🔍 | 正在查重检测 |\n| 待审核 | 📝 | 查重完成，等待导师审核 |\n| 审核中 | 👀 | 导师正在审核 |\n| 需修改 | ✏️ | 退回修改 |\n| 已通过 | ✅ | 审核通过 |\n| 已拒绝 | ❌ | 审核不通过 |\n','## 审核状态一览\n\n| 状态 | 图标 | 说明 |\n|------|------|------|\n| 待提交 | ⏳ | 尚未提交论文 |\n| 待分配 | 📋 | 等待分配导师 |\n| 已分配 | 👤 | 导师已分配 |\n| 查重中 | 🔍 | 正在查重检测 |\n| 待审核 | 📝 | 查重完成，等待导师审核 |\n| 审核中 | 👀 | 导师正在审核 |\n| 需修改 | ✏️ | 退回修改 |',707990676976578560,'',0,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-09 00:22:11',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677161127936,'查重失败怎么办？','## 查重失败的常见原因\n\n1. **文件格式不支持** — 请确认上传的是 .doc / .docx / .pdf 格式\n2. **文件损坏** — 尝试重新保存文件后上传\n3. **文件为扫描版** — PDF 需包含可编辑文本层\n4. **文件过大** — 超过 200MB 限制，请压缩图片后重试\n5. **系统繁忙** — 稍后重新提交查重任务\n\n如果多次失败，请联系管理员或导师协助处理。\n','## 查重失败的常见原因\n\n1. **文件格式不支持** — 请确认上传的是 .doc / .docx / .pdf 格式\n2. **文件损坏** — 尝试重新保存文件后上传\n3. **文件为扫描版** — PDF 需包含可编辑文本层\n4. **文件过大** — 超过 200MB 限制，请压缩图片后重试\n5. **系统繁忙** — 稍后重新提交查重任务\n\n如果多次失败，请联系管理员或导师协助处理。',707990676984967168,'',0,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-09 00:22:11',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677165322240,'查重结果不准确怎么办？','## 查重结果异常处理\n\n### 相似度偏高\n1. 检查论文中引用的参考文献是否标注正确\n2. 查看相似来源详情，确认是否为合理引用\n3. 修改高亮段落后重新查重\n\n### 相似度偏低\n1. 确实独立完成 → 恭喜！\n2. 怀疑检测不准确 → 联系管理员开启第三方查重\n\n> 系统支持多次查重，每次查重间隔受系统配置限制。\n','## 查重结果异常处理\n\n### 相似度偏高\n1. 检查论文中引用的参考文献是否标注正确\n2. 查看相似来源详情，确认是否为合理引用\n3. 修改高亮段落后重新查重\n\n### 相似度偏低\n1. 确实独立完成 → 恭喜！\n2. 怀疑检测不准确 → 联系管理员开启第三方查重\n\n> 系统支持多次查重，每次查重间隔受系统配置限制。\n',707990676984967168,'',2,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-24 22:04:37',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677173710848,'什么时候可以重新提交查重？','## 重新查重机制\n\n### 查重间隔\n- 系统默认配置：每次查重间隔 **5 分钟**\n- 每日查重次数上限：**5 次**（管理员可调整）\n\n### 重新提交场景\n- 修改论文后需重新查重\n- 导师要求修改后重查\n- 撤回后重新提交\n\n### 操作方式\n在\"我的论文\"页面，点击\"重新查重\"按钮即可。\n','## 重新查重机制\n\n### 查重间隔\n- 系统默认配置：每次查重间隔 **5 分钟**\n- 每日查重次数上限：**5 次**（管理员可调整）\n\n### 重新提交场景\n- 修改论文后需重新查重\n- 导师要求修改后重查\n- 撤回后重新提交\n\n### 操作方式\n在\"我的论文\"页面，点击\"重新查重\"按钮即可。\n',707990676984967168,'',0,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-09 00:22:11',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677177905152,'如何联系导师？','## 联系导师的方式\n\n### 在线聊天\n进入\"导师互动\"页面，可以与导师实时文字沟通、发送文件。\n\n### 站内消息\n通过消息中心发送私信，消息会推送到导师的通知栏。\n\n### 审核反馈\n导师审核论文后会给出书面意见，可在论文详情页查看。\n\n> 建议优先使用系统内置的沟通工具，便于保留沟通记录。\n','## 联系导师的方式\n\n### 在线聊天\n进入\"导师互动\"页面，可以与导师实时文字沟通、发送文件。\n\n### 站内消息\n通过消息中心发送私信，消息会推送到导师的通知栏。\n\n### 审核反馈\n导师审核论文后会给出书面意见，可在论文详情页查看。\n\n> 建议优先使用系统内置的沟通工具，便于保留沟通记录。\n',707990676984967168,'',0,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-09 00:22:11',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677186293760,'忘记密码如何找回？','## 密码重置流程\n\n1. 在登录页点击\"忘记密码\"\n2. 输入注册时使用的邮箱地址\n3. 系统发送验证码到邮箱\n4. 输入验证码后设置新密码\n5. 使用新密码登录\n\n> 如未绑定邮箱，请联系管理员重置密码。\n','## 密码重置流程\n\n1. 在登录页点击\"忘记密码\"\n2. 输入注册时使用的邮箱地址\n3. 系统发送验证码到邮箱\n4. 输入验证码后设置新密码\n5. 使用新密码登录\n\n> 如未绑定邮箱，请联系管理员重置密码。\n',707990676984967168,'',0,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-09 00:22:11',0);
INSERT  IGNORE INTO `knowledge_article` (`id`, `title`, `content`, `summary`, `category_id`, `tags`, `view_count`, `status`, `sort`, `author_id`, `author_name`, `create_time`, `update_time`, `is_deleted`) VALUES (707990677190488064,'系统更新日志','## 系统功能更新\n\n### v1.0 功能\n- 论文提交与格式校验\n- SimHash 本地查重引擎\n- 教师在线审核工作流\n- 导师-学生即时通讯\n- 学术诚信教育资源\n- 知识库帮助中心\n\n### 即将上线\n- 第三方查重引擎接入\n- 深度学习相似度检测\n- 移动端适配优化\n','## 系统功能更新\n\n### v1.0 功能\n- 论文提交与格式校验\n- SimHash 本地查重引擎\n- 教师在线审核工作流\n- 导师-学生即时通讯\n- 学术诚信教育资源\n- 知识库帮助中心\n\n### 即将上线\n- 第三方查重引擎接入\n- 深度学习相似度检测\n- 移动端适配优化\n',707990676997550080,'',0,'PUBLISHED',0,0,'系统管理员','2026-05-09 00:22:11','2026-05-09 00:22:11',0);

-- ---------- 段 5B 自检 ----------
-- expected: articles=15 orphan_articles=0 published=15
SELECT (SELECT COUNT(*) FROM `knowledge_article`  WHERE `is_deleted` = 0) AS articles,
       (SELECT COUNT(*) FROM `knowledge_article` a LEFT JOIN `knowledge_category` c
          ON a.`category_id` = c.`id` WHERE c.`id` IS NULL) AS orphan_articles,
       (SELECT COUNT(*) FROM `knowledge_article`
         WHERE `status` = 'PUBLISHED' AND `is_deleted` = 0) AS published;


-- ============================================================================
-- 段 6：消息模板 message_template
--
-- MessageServiceImpl.sendMessageByTemplate 查的是
--   SELECT * FROM message_template WHERE template_code = ? AND is_active = 1
-- 少一行对应 code，那类通知就整条发不出去，且只在日志里留一句告警。
--
-- 占位符必须写 {key}。渲染工具（TemplateRenderUtils）只认这一种写法，而且
-- 写成 ${key} 并不是「原样输出」那么无害 —— ${paperTitle} 里正好包含子串
-- {paperTitle}，会被替换掉、留下一个孤零零的 $，通知正文一直是
-- 「您的论文《$论文标题》…」。管理端预览走同一个渲染工具，从预览里看不出来。
-- （11 行历史 ${key} 已于 2026-10-12 全部修正为 {key}，原 补消息模板.sql
--   内容归并进本段）
--
-- 变量名必须与调用方 params.put(...) 的键完全一致，否则会原样留下
-- "{paperTitle}" 这种字面量。
-- 改了模板里的变量名，务必同步改调用点（见 NotificationService 类注释）。
-- ============================================================================

INSERT INTO `message_template` (`id`, `template_code`, `template_name`, `template_type`, `title_template`, `content_template`, `is_active`, `create_time`, `update_time`) VALUES 
(1,'TEACHER_ASSIGNMENT_NOTICE','教师分配通知','assignment','新的论文指导任务待确认','尊敬的{teacherName}老师：\n\n您有一个新的论文指导任务需要确认。\n\n论文标题：{paperTitle}\n论文ID：{paperId}\n\n请于 {deadline} 前在系统中确认是否接收此指导任务。如超时未确认，系统将自动重新分配。\n\n感谢您的配合！',1,'2025-11-22 15:31:00','2025-11-22 15:31:00'),
(2,'TEACHER_CONFIRM_NOTICE','教师确认结果通知','confirm','你的论文指导老师已确认','亲爱的{studentName}同学：\n\n你的论文《{paperTitle}》的指导老师{teacherName}已确认接收指导任务，后续将由{teacherName}老师为你提供指导。\n\n确认时间：{confirmTime}',1,'2025-11-22 15:31:00','2025-11-22 15:31:00'),
(3,'TEACHER_REJECT_NOTICE','教师拒绝结果通知','reject','你的论文指导任务被拒绝','亲爱的{studentName}同学：\n\n很抱歉，你的论文《{paperTitle}》的指导老师{teacherName}未能接收指导任务。\n\n拒绝原因：{rejectReason}\n\n系统将为你重新分配其他指导老师，请耐心等待。\n\n拒绝时间：{rejectTime}',1,'2025-11-22 15:31:00','2025-11-22 15:31:00'),
(4,'STUDENT_CONFIRM_RESULT','学生确认结果通知','confirm','论文指导老师确认结果','同学：\n\n您的论文《{paperTitle}》的指导老师{teacherName}已{confirmed}接收指导任务。\n\n{rejectReason}',1,'2025-11-22 15:31:00','2025-11-22 15:31:00'),
(5,'TIMEOUT_REJECT_NOTICE','超时拒绝通知','reject','论文指导任务超时自动拒绝','亲爱的{studentName}同学：\n\n您的论文《{paperTitle}》的指导老师{teacherName}在指定时间内未确认接收指导任务，系统已自动拒绝并释放该分配。\n\n超时时间：{timeoutTime}\n\n系统将为您重新分配其他指导老师，请耐心等待。',1,'2025-11-22 16:42:02','2025-11-22 16:42:02'),
(6,'SYSTEM_NOTICE','系统通知','system','{title}','{content}\n时间：{sendTime}',1,'2025-11-22 18:27:37','2025-11-22 21:49:04'),
(7,'PAPER_SYSTEM_NOTICE','论文系统通知','system','{title}','{content}\n论文：{paperTitle}\n时间：{sendTime}',1,'2025-11-22 18:27:37','2025-11-22 21:49:16'),
(8,'STUDENT_ASSIGNMENT_RESULT','学生分配结果通知','assignment','指导老师分配成功','亲爱的{studentName}同学：\n您的论文《{paperTitle}》已成功分配指导老师{teacherName}。\n分配时间：{assignmentTime}',1,'2025-11-22 18:27:37','2025-11-22 21:49:24'),
(9,'PAPER_SUBMIT_SUCCESS','论文提交成功','system','论文提交成功','亲爱的{userName}同学：\n您的论文《{paperTitle}》已成功提交，系统将自动分配指导老师并进行查重检测。\n提交时间：{sendTime}',1,'2025-11-22 18:27:37','2025-11-22 21:49:33'),
(10,'PAPER_SUBMIT_FAILED','论文提交失败','system','论文提交失败','亲爱的{userName}同学：\n您的论文《{paperTitle}》提交失败。\n失败原因：{content}\n请稍后重试或联系管理员。',1,'2025-11-22 18:27:37','2025-11-22 21:49:40'),
(11,'ADVISOR_ASSIGN_SUCCESS','指导老师分配成功','assignment','指导老师分配成功','亲爱的{userName}同学：\n您的论文《{paperTitle}》已成功分配指导老师，系统将开始进行查重检测。\n分配时间：{sendTime}',1,'2025-11-22 18:27:37','2025-11-22 21:49:50'),
(12,'ADVISOR_ASSIGN_FAILED','指导老师分配失败','assignment','指导老师分配失败','亲爱的{userName}同学：\n您的论文《{paperTitle}》指导老师分配失败。\n失败原因：{content}\n请稍后重试或联系管理员。',1,'2025-11-22 18:27:37','2025-11-22 21:49:58'),
(16,'CHECK_SUCCESS','查重成功通知','system','查重检测完成','亲爱的{userName}同学：\n您的论文《{paperTitle}》查重检测已完成。\n相似度：{content}%\n检测结果：符合要求，论文已进入审核阶段。',1,'2025-11-22 18:27:37','2025-11-22 21:50:06'),
(17,'CHECK_FAILED','查重失败通知','system','查重检测失败','亲爱的{userName}同学：\n您的论文《{paperTitle}》查重检测失败。\n失败原因：{content}\n请根据要求修改后重新提交。',1,'2025-11-22 18:27:37','2025-11-22 21:50:11'),
(18,'AUDIT_SUCCESS','审核成功通知','system','论文审核通过','恭喜{userName}同学！\n您的论文《{paperTitle}》已通过审核。\n审核意见：{content}\n审核人：{teacherName}',1,'2025-11-22 18:27:37','2025-11-22 21:50:23'),
(19,'AUDIT_FAILED','审核失败通知','system','论文审核未通过','亲爱的{userName}同学：\n您的论文《{paperTitle}》未通过审核。\n审核意见：{content}\n审核人：{teacherName}\n请根据意见修改后重新提交。',1,'2025-11-22 18:27:37','2025-11-22 21:50:31')
ON DUPLICATE KEY UPDATE `template_name` = VALUES(`template_name`), `template_type` = VALUES(`template_type`), `title_template` = VALUES(`title_template`), `content_template` = VALUES(`content_template`), `is_active` = VALUES(`is_active`);

-- ---------------------------------------------------------------------------
-- 段 6 补充：代码正在用、而库里缺的 3 个模板
--
-- 发送点逐个核对过，占位符与实际传入的键一一对应：
--   PAPER_STATUS_CHANGE     NotificationServiceImpl.sendPaperStatusChangeNotification
--                           studentId,paperId,paperTitle,newStatus,remarks,changeTime
--   PAPER_REVIEW_RESULT     NotificationServiceImpl.sendPaperReviewResultNotification
--                           studentId,paperId,paperTitle,reviewResult,reviewComments,reviewTime
--   SIMILARITY_CHECK_RESULT NotificationServiceImpl.sendSimilarityCheckResultNotification
--                           studentId,paperId,paperTitle,similarityRate,riskLevel,checkTime
--
-- 占用的 id 13/14/15 是空闲号（真实库已占 1-12、16-19），不会覆盖任何既有模板。
--
-- ⚠️ {riskLevel} 传进来时已经是「低风险 / 中等风险 / 高风险 / 极高风险」整词
--    （RiskLevelUtils.of），所以模板里不要再补一个「风险」字，
--    否则会渲染成「中等风险风险」。
-- ---------------------------------------------------------------------------
INSERT INTO `message_template`
  (`id`, `template_code`, `template_name`, `template_type`,
   `title_template`, `content_template`, `is_active`)
VALUES
  (13, 'PAPER_STATUS_CHANGE', '论文状态变更通知', 'notice',
   '论文状态变更通知',
   '您的论文《{paperTitle}》状态已更新为：{newStatus}\n备注：{remarks}\n变更时间：{changeTime}\n请及时查看并处理相关事宜。',
   1),

  (14, 'PAPER_REVIEW_RESULT', '论文审核结果通知', 'notice',
   '论文审核结果通知',
   '您的论文《{paperTitle}》审核结果：{reviewResult}\n审核意见：{reviewComments}\n审核时间：{reviewTime}\n请登录系统查看详情。',
   1),

  (15, 'SIMILARITY_CHECK_RESULT', '论文查重结果通知', 'notice',
   '论文查重结果通知',
   '您的论文《{paperTitle}》查重已完成。\n相似度：{similarityRate}%（{riskLevel}）\n查重时间：{checkTime}\n请及时查看详细报告。',
   1)
ON DUPLICATE KEY UPDATE
  `template_name`    = VALUES(`template_name`),
  `template_type`    = VALUES(`template_type`),
  `title_template`   = VALUES(`title_template`),
  `content_template` = VALUES(`content_template`),
  `is_active`        = VALUES(`is_active`);


-- ============================================================================
-- 段 7：查重规则 check_rule
--
-- 为什么要有这一段：学生端「当前查重规则」卡片走的
-- GET /api/student/check-rule/current，取的是 is_default=1 且未删除的那一条，
-- 一条都取不到就返回 404 / 40401「未配置默认查重规则」。本文件此前没有 check_rule
-- 的种子，线上这张表是空的，卡片就一直报错。
--
-- 取值沿用老库现有的这 4 条（id / 阈值 / 次数 / 间隔 / 描述逐字段照抄）：
--   UNDERGRADUATE 本科（默认）  MASTER 硕士  PHD 博士  COURSEWORK 课程作业
--
-- 几个容易踩的点：
--   1) id 是写死的固定值。命中主键就更新业务列，所以本段可重复执行；
--      线上空表跑完就是这 4 条。
--   2) is_default 只在 INSERT 时给，**更新列表里故意不含它**。否则线上若已有
--      一条默认规则，再跑一遍本段就会出现两条 is_default=1。读取端
--      （StudentCheckRuleServiceImpl）按 id 倒序取第一条，线上自建规则的雪花 id
--      远大于这里的固定 id，因此真正生效的仍是线上那条 —— 这个优先级保持不变。
--   3) pass_threshold 是 NOT NULL 的历史列，但**已废弃**：管理端编辑规则时不写它，
--      真正生效的合格线由 SimilarityThresholdResolver 按「专业 > 学院 > 全局 >
--      yml 默认 20.00」解析。这里填的值只为满足 NOT NULL，不参与判定，
--      所以它和界面上显示的阈值对不上是正常的。
--   4) compare_lib 同样是 NOT NULL 的历史列，存的是库名 JSON 文本，
--      和 compare_lib 表没有任何关联（判定时走 CheckTaskServiceImpl
--      .loadCompareLibraryData，是全量取「启用 + LOCAL」的库）。照抄老库取值即可，
--      不要当成外键去改。
--   5) create_by / update_by 沿用老库的 10001 / 10002。本库 sys_user 里没有这两个
--      id（该列无外键约束，代码也不读这两列），仅作溯源记录，别去补用户。
--   6) 本段不碰 is_deleted：某条规则被管理端软删过的话，重跑本段不会把它恢复。
--
-- ⚠️ 只想给线上补数据的话，**单独跑这一段就够了**，千万别整份文件跑
--    —— 段 1~3 是 college / major 整表替换 + 全库引用改绑。
-- ============================================================================

INSERT INTO `check_rule`
  (`id`, `rule_name`, `rule_code`, `pass_threshold`, `max_re_submit_count`,
   `compare_lib`, `check_interval`, `max_check_count`, `is_default`, `description`,
   `create_by`, `update_by`)
VALUES
  (1623456789001, '本科毕业论文查重规则', 'UNDERGRADUATE', 20.00, 3,
   '["校内论文库","知网期刊库","互联网资源"]', 86400, 5, 1,
   '适用于本科毕业论文查重，重复率≤20%为合格', 10001, 10001),

  (1623456789002, '硕士研究生论文查重规则', 'MASTER', 15.00, 3,
   '["校内论文库","知网硕博库","知网期刊库","万方数据库","互联网资源"]', 172800, 3, 0,
   '适用于硕士研究生论文查重，重复率≤15%为合格', 10001, 10002),

  (1623456789003, '博士研究生论文查重规则', 'PHD', 10.00, 3,
   '["校内论文库","知网硕博库","知网期刊库","万方数据库","维普数据库","互联网资源","国际学术库"]', 259200, 2, 0,
   '适用于博士研究生论文查重，重复率≤10%为合格', 10002, 10002),

  (1623456789005, '课程作业查重规则', 'COURSEWORK', 30.00, 3,
   '["校内作业库","互联网资源"]', 3600, 1, 0,
   '适用于课程作业查重，重复率≤30%为合格', 10001, 10001)
ON DUPLICATE KEY UPDATE
  `rule_name`           = VALUES(`rule_name`),
  `rule_code`           = VALUES(`rule_code`),
  `pass_threshold`      = VALUES(`pass_threshold`),
  `max_re_submit_count` = VALUES(`max_re_submit_count`),
  `compare_lib`         = VALUES(`compare_lib`),
  `check_interval`      = VALUES(`check_interval`),
  `max_check_count`     = VALUES(`max_check_count`),
  `description`         = VALUES(`description`);
  -- is_default / create_by / update_by / is_deleted 故意不更新，原因见上面 2) 5) 6)


-- ============================================================================
-- 段 8：自检
-- ============================================================================

-- 8.1 学院数应为 20，专业数应为 114，孤儿专业（指不到学院）应为 0
SELECT (SELECT COUNT(*) FROM college WHERE is_deleted = 0) AS colleges,
       (SELECT COUNT(*) FROM major   WHERE is_deleted = 0) AS majors,
       (SELECT COUNT(*) FROM major m LEFT JOIN college c ON m.college_id = c.id
         WHERE c.id IS NULL) AS orphan_majors;

-- 8.2 各学院专业数分布（真实院校本就不均匀，这里只为看一眼有没有整段漏掉）
SELECT c.college_code, c.college_name, COUNT(m.id) AS major_count
  FROM college c LEFT JOIN major m ON m.college_id = c.id AND m.is_deleted = 0
 GROUP BY c.id, c.college_code, c.college_name ORDER BY c.id;

-- 8.3 旧 id 是否还残留引用（三项都应为 0）
SELECT (SELECT COUNT(*) FROM sys_user     WHERE college_id BETWEEN 1668572928000 AND 1668572928059) AS old_sys_user,
       (SELECT COUNT(*) FROM paper_info   WHERE college_id BETWEEN 1668572928000 AND 1668572928059
                                          OR major_id   BETWEEN 1668572928000 AND 1668572928059) AS old_paper_info,
       (SELECT COUNT(*) FROM student_info WHERE college_id BETWEEN 1668572928000 AND 1668572928059
                                          OR major_id   BETWEEN 1668572928000 AND 1668572928059) AS old_student_info;

-- 8.4 字典与种子行数
SELECT (SELECT COUNT(*) FROM sys_operation_type) AS operation_types,   -- 197
       (SELECT COUNT(*) FROM knowledge_category) AS knowledge_categories, -- 6
       (SELECT COUNT(*) FROM message_template)   AS message_templates;    -- 19

-- 8.5 查重规则：seed_rules 至少 4，default_rules 必须恰好 1
--     default_rules = 0 → 学生端「当前查重规则」卡片报 404/40401；
--     > 1 → 学生端按 id 倒序取，拿到的是 id 最大的那条，容易和预期不符
SELECT (SELECT COUNT(*) FROM check_rule WHERE is_deleted = 0)                   AS seed_rules,
       (SELECT COUNT(*) FROM check_rule WHERE is_deleted = 0 AND is_default = 1) AS default_rules;

-- 8.6 代码在用的模板 code 是否覆盖齐（缺一行就有一类通知发不出去）
--     期望：missing 为空
SELECT GROUP_CONCAT(c.code ORDER BY c.code) AS missing
  FROM (
        SELECT 'PAPER_STATUS_CHANGE'     AS code UNION ALL
        SELECT 'PAPER_REVIEW_RESULT'            UNION ALL
        SELECT 'SIMILARITY_CHECK_RESULT'        UNION ALL
        SELECT 'TEACHER_ASSIGNMENT_NOTICE'      UNION ALL
        SELECT 'TEACHER_CONFIRM_NOTICE'         UNION ALL
        SELECT 'TEACHER_REJECT_NOTICE'          UNION ALL
        SELECT 'TIMEOUT_REJECT_NOTICE'          UNION ALL
        SELECT 'STUDENT_ASSIGNMENT_RESULT'      UNION ALL
        SELECT 'SYSTEM_NOTICE'
       ) c
  LEFT JOIN message_template t ON t.template_code = c.code AND t.is_active = 1
 WHERE t.id IS NULL;

-- 8.7 学科领域（段 3B）：subjects=100 / level1=10 / level2=90 / orphan_subject=0
--     orphan 指 level=2 但 parent_id 指不到 level=1 的行（含 parent_id 为 NULL 的二级）。
--     level 与 parent_id 不自洽（如 level=1 却带 parent_id）由 selfcheck_clash 暴露。
SELECT (SELECT COUNT(*) FROM subject WHERE is_deleted = 0)                     AS subjects,
       (SELECT COUNT(*) FROM subject WHERE is_deleted = 0 AND level = 1)        AS level1,
       (SELECT COUNT(*) FROM subject WHERE is_deleted = 0 AND level = 2)        AS level2,
       (SELECT COUNT(*) FROM subject s LEFT JOIN subject p ON s.parent_id = p.id
         WHERE s.level = 2 AND (p.id IS NULL OR p.level <> 1))                  AS orphan_subject,
       (SELECT COUNT(*) FROM subject WHERE is_deleted = 0
          AND ((level = 1 AND parent_id IS NOT NULL) OR (level = 2 AND parent_id IS NULL))) AS selfcheck_clash;

-- 8.8 专业映射（段 3B）：两列都不该指到不存在的行
--     mapped_majors=108（114 个专业里哲学/历史学/体育类 4 个共 6 个无对应学科）
--     mapped_subjects 覆盖门类+二级；pairs 为映射行数
SELECT (SELECT COUNT(*) FROM major_subject WHERE is_deleted = 0)                       AS pairs,
       (SELECT COUNT(DISTINCT major_id) FROM major_subject WHERE is_deleted = 0)       AS mapped_majors,
       (SELECT COUNT(DISTINCT subject_id) FROM major_subject WHERE is_deleted = 0)     AS mapped_subjects,
       (SELECT COUNT(*) FROM major_subject ms
          LEFT JOIN major   m ON ms.major_id   = m.id
          LEFT JOIN subject s ON ms.subject_id = s.id
         WHERE ms.is_deleted = 0 AND (m.id IS NULL OR s.id IS NULL))                   AS dangling_should_be_0;


-- ============================================================================
-- 段 9：审核意见模板 review_template（10 条公开模板）
--
-- 教师端「审核意见模板」页（ReviewTemplates.vue）上线后库里一行没有，
-- 页面五个分类全是 Empty。这里播 10 条常用模板，覆盖全部 5 个分类。
--
-- 字段口径（与 ReviewTemplate 实体 / ReviewTemplateServiceImpl 一致）：
--   category   取值域由前端定义：approve-通过 / modify-修改 / reject-拒绝 /
--              suggest-建议 / format-格式，后端不做白名单校验。
--   scenarios  MySQL json 列，Java 侧映射成 String，格式是 JSON 数组文本，
--              元素只能取「初审 / 复审 / 终审」（前端 checkbox 的三个选项）。
--   is_public  = 1：公开模板所有教师可见可用（list() 的过滤条件是
--              create_by = 当前用户 OR is_public = 1）。种子播的必须是公开，
--              否则除了 create_by 指向的那个人，谁也看不到。
--   create_by  用系统兜底管理员 ID（同 MyMetaObjectHandler 匿名填充值）。
--   usage_count / last_used 初始 0 / NULL，被「使用」后由 use() 自增维护。
--
-- 幂等：id 写死 + ON DUPLICATE KEY UPDATE，可重复执行；不会动 usage_count
--       和 last_used（那是运行期数据，重播种子不该把使用统计清零）。
-- （原 补审核意见模板.sql 内容归并进本段，2026-10-12）
-- ============================================================================

INSERT INTO `review_template` (`id`, `title`, `category`, `content`, `scenarios`, `is_public`, `usage_count`, `last_used`, `create_by`, `create_time`, `update_by`, `update_time`, `is_deleted`) VALUES
-- ---------- approve 通过类 ----------
(1668700000000000001, '整体通过', 'approve', '论文选题合理，结构完整，论述清晰，查重率符合学校要求。同意通过审核，可进入下一环节。', '["初审","复审","终审"]', 1, 0, NULL, 1546278765432123458, NOW(), 1546278765432123458, NOW(), 0),
(1668700000000000002, '通过-附修改建议', 'approve', '论文整体质量良好，同意通过审核。建议在答辩前注意以下几点：\n1. 进一步校对图表编号与正文的对应关系；\n2. 参考文献格式按学校模板统一；\n3. 摘要部分可再精炼，突出创新点。', '["复审","终审"]', 1, 0, NULL, 1546278765432123458, NOW(), 1546278765432123458, NOW(), 0),
-- ---------- modify 修改类 ----------
(1668700000000000003, '查重率偏高需降重', 'modify', '论文查重率超出合格阈值，请重点修改以下内容：\n1. 标红段落用自己的语言重新表述，避免整句引用；\n2. 必要的引用请规范标注出处；\n3. 文献综述部分建议归纳转述，不要简单堆砌原文。\n修改后请重新提交查重。', '["初审","复审"]', 1, 0, NULL, 1546278765432123458, NOW(), 1546278765432123458, NOW(), 0),
(1668700000000000004, '结构需调整', 'modify', '论文框架基本合理，但部分章节安排需要调整：\n1. 相关工作与本文方法的边界不清晰，建议分开阐述；\n2. 实验部分应补充对照实验的设置说明；\n3. 结论部分需回应引言中提出的研究问题。\n请按上述意见修改后再次提交。', '["初审","复审"]', 1, 0, NULL, 1546278765432123458, NOW(), 1546278765432123458, NOW(), 0),
(1668700000000000005, '数据分析不充分', 'modify', '论文实验数据收集完整，但分析深度不足：\n1. 建议对实验结果进行统计分析，给出显著性检验；\n2. 图表需要有解读文字，不能仅罗列数据；\n3. 异常数据应说明原因或剔除依据。\n请补充完善后重新提交。', '["复审"]', 1, 0, NULL, 1546278765432123458, NOW(), 1546278765432123458, NOW(), 0),
-- ---------- reject 拒绝类 ----------
(1668700000000000006, '查重严重超标-拒绝', 'reject', '论文查重率严重超出学校规定的合格标准，存在大段与他人文献雷同的内容，本次审核不予通过。请认真重写相关章节，杜绝抄袭行为，修改达到要求后再行提交。', '["初审","复审","终审"]', 1, 0, NULL, 1546278765432123458, NOW(), 1546278765432123458, NOW(), 0),
(1668700000000000007, '选题不符-拒绝', 'reject', '论文选题与本专业培养目标不符，或研究内容严重偏离开题报告确定的方向，本次审核不予通过。请与指导教师沟通重新确定选题后再提交。', '["初审"]', 1, 0, NULL, 1546278765432123458, NOW(), 1546278765432123458, NOW(), 0),
-- ---------- suggest 建议类 ----------
(1668700000000000008, '文献综述建议', 'suggest', '文献综述部分建议：\n1. 按研究脉络组织，而不是逐篇罗列；\n2. 补充近三年的高水平文献，体现研究前沿；\n3. 结尾应指出现有研究的不足，引出本文的切入点。', '["初审","复审"]', 1, 0, NULL, 1546278765432123458, NOW(), 1546278765432123458, NOW(), 0),
(1668700000000000009, '摘要写作建议', 'suggest', '摘要建议按「目的—方法—结果—结论」四要素组织：\n1. 首句点明研究背景与目的；\n2. 简要说明采用的方法；\n3. 给出量化的主要结果；\n4. 结论部分突出创新点，避免与引言重复。', '["初审","复审","终审"]', 1, 0, NULL, 1546278765432123458, NOW(), 1546278765432123458, NOW(), 0),
-- ---------- format 格式类 ----------
(1668700000000000010, '格式规范检查', 'format', '请按学校论文模板检查以下格式问题：\n1. 图表标题位置（图下表上）及编号是否连续；\n2. 参考文献著录格式是否符合 GB/T 7714；\n3. 页眉页脚、页码格式是否统一；\n4. 公式编号是否右对齐并连续编号。', '["初审","复审","终审"]', 1, 0, NULL, 1546278765432123458, NOW(), 1546278765432123458, NOW(), 0)
ON DUPLICATE KEY UPDATE
  `title` = VALUES(`title`),
  `category` = VALUES(`category`),
  `content` = VALUES(`content`),
  `scenarios` = VALUES(`scenarios`),
  `is_public` = VALUES(`is_public`),
  `update_time` = NOW();

-- ---------- 段 9 自检：期望 seed_public=10（templates>=10，教师自建会让它更大，属正常） ----------
SELECT COUNT(*) AS templates,
       (SELECT COUNT(*) FROM `review_template` WHERE id BETWEEN 1668700000000000001 AND 1668700000000000010 AND is_public = 1 AND is_deleted = 0) AS seed_public,
       COUNT(DISTINCT category) AS categories
FROM `review_template`
WHERE `is_deleted` = 0;


-- ============================================================================
-- 段 10：论文全生命周期字典（70xx~74xx）
--
-- 历史上种子数据从未给 paper_status 播种，前端状态标签一直靠硬编码兜底；
-- P1~P6 各期又陆续引入评阅 / 答辩 / 学位类型 / 导师评价标签字典。
-- 这里一次性播齐，ID 固定：70xx 论文状态、71xx 评阅任务、72xx 答辩、
-- 73xx 学位类型、74xx 导师评价标签。全部 INSERT IGNORE，可重复执行。
-- 配套表结构在 init.sql（空库）与 存量修复.sql 段 1.16（老库）。
-- （原 补论文全生命周期P1/P2/P3/P5/P6.sql 的字典段归并进本段，2026-10-12）
-- ============================================================================

-- ---------- 10.1 paper_status（P1）：7 个存量状态 + 6 个阶段门状态 ----------
INSERT IGNORE INTO `sys_dict_type`
  (`id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7000, '论文状态', 'paper_status', '0', '1546278765432123458', NOW(),
   '论文全生命周期状态（P1 扩展：新增评阅/答辩/归档阶段门）');

INSERT IGNORE INTO `sys_dict_data`
  (`id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7001, 1,  '待分配',    'pending',        'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态'),
  (7002, 2,  '已分配',    'assigned',       'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态'),
  (7003, 3,  '待查重',    'checking',       'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态'),
  (7004, 4,  '待审核',    'auditing',       'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态（语义收窄为导师初审）'),
  (7005, 5,  '审核通过',  'completed',      'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态（语义收窄为初审通过）'),
  (7006, 6,  '审核不通过','rejected',       'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态'),
  (7007, 7,  '已取消',    'withdrawn',      'paper_status', 'N', '0', '1546278765432123458', NOW(), '存量状态'),
  (7008, 8,  '评阅中',    'reviewing',      'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P2 评阅任务启用）'),
  (7009, 9,  '中期答辩',  'mid_defense',    'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P3 答辩管理启用）'),
  (7010, 10, '终期答辩',  'final_defense',  'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P3 答辩管理启用）'),
  (7011, 11, '答辩通过',  'defense_passed', 'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P3 答辩管理启用）'),
  (7012, 12, '定稿',      'finalized',      'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P4 归档启用）'),
  (7013, 13, '已归档',    'archived',       'paper_status', 'N', '0', '1546278765432123458', NOW(), 'P1 新增阶段门（P4 归档启用）');

-- ---------- 10.2 评阅任务字典（P2）：状态 / 类型 / 方式 ----------
INSERT IGNORE INTO `sys_dict_type`
  (`id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7100, '评阅任务状态', 'review_assign_status', '0', '1546278765432123458', NOW(), '多人评阅任务状态（论文全生命周期P2）'),
  (7110, '评阅任务类型', 'review_assign_type',   '0', '1546278765432123458', NOW(), '导师评阅/交叉评阅/盲审（论文全生命周期P2）'),
  (7120, '评阅任务方式', 'review_assign_method', '0', '1546278765432123458', NOW(), '线上评阅/线下评阅结果回填（论文全生命周期P2）');

INSERT IGNORE INTO `sys_dict_data`
  (`id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7101, 1, '待评阅',     '0', 'review_assign_status', 'N', '0', '1546278765432123458', NOW(), '任务已下发'),
  (7102, 2, '评阅中',     '1', 'review_assign_status', 'N', '0', '1546278765432123458', NOW(), '预留（P2 未开放开始动作）'),
  (7103, 3, '已提交',     '2', 'review_assign_status', 'N', '0', '1546278765432123458', NOW(), '评阅结果已提交'),
  (7104, 4, '已超时转派', '3', 'review_assign_status', 'N', '0', '1546278765432123458', NOW(), '超时扫描标记，原任务失效'),
  (7111, 1, '导师评阅',   'advisor', 'review_assign_type', 'N', '0', '1546278765432123458', NOW(), ''),
  (7112, 2, '交叉评阅',   'peer',    'review_assign_type', 'N', '0', '1546278765432123458', NOW(), '默认类型'),
  (7113, 3, '盲审',       'blind',   'review_assign_type', 'N', '0', '1546278765432123458', NOW(), '查询层对学生/导师信息脱敏'),
  (7121, 1, '线上评阅',   'online',  'review_assign_method', 'N', '0', '1546278765432123458', NOW(), '系统内直接打分'),
  (7122, 2, '线下评阅',   'offline', 'review_assign_method', 'N', '0', '1546278765432123458', NOW(), '结果回填 + 扫描件佐证');

-- ---------- 10.3 答辩字典（P3）：批次状态 / 批次类型 / 方式 / 组角色 / 结论 / 等级 ----------
INSERT IGNORE INTO `sys_dict_type`
  (`id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7200, '答辩批次状态', 'defense_batch_status', '0', '1546278765432123458', NOW(), '答辩批次状态（论文全生命周期P3）'),
  (7210, '答辩批次类型', 'defense_batch_type',   '0', '1546278765432123458', NOW(), '中期/终期共用（论文全生命周期P3）'),
  (7220, '答辩方式',     'defense_mode',         '0', '1546278765432123458', NOW(), '线上/线下（论文全生命周期P3）'),
  (7230, '答辩组角色',   'defense_group_role',   '0', '1546278765432123458', NOW(), '组长/成员（论文全生命周期P3）'),
  (7240, '答辩结论',     'defense_result',       '0', '1546278765432123458', NOW(), '通过/不通过/缓辩（论文全生命周期P3）'),
  (7250, '答辩等级',     'defense_grade',        '0', '1546278765432123458', NOW(), '优/良/中/及格/不及格（论文全生命周期P3）');

INSERT IGNORE INTO `sys_dict_data`
  (`id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7201, 1, '筹备中', '0', 'defense_batch_status', 'Y', '0', '1546278765432123458', NOW(), '批次新建，未开始答辩'),
  (7202, 2, '进行中', '1', 'defense_batch_status', 'N', '0', '1546278765432123458', NOW(), '答辩进行中'),
  (7203, 3, '已结束', '2', 'defense_batch_status', 'N', '0', '1546278765432123458', NOW(), '批次已结束（组长已汇总提交结论）'),
  (7211, 1, '中期答辩', 'mid_term', 'defense_batch_type', 'N', '0', '1546278765432123458', NOW(), '中期检查答辩'),
  (7212, 2, '终期答辩', 'final',    'defense_batch_type', 'Y', '0', '1546278765432123458', NOW(), '终期/毕业答辩（成绩回写 final_score）'),
  (7221, 1, '线上答辩', 'online',  'defense_mode', 'N', '0', '1546278765432123458', NOW(), '线上会议，location 填会议链接'),
  (7222, 2, '线下答辩', 'offline', 'defense_mode', 'Y', '0', '1546278765432123458', NOW(), '线下教室，location 填教室地址'),
  (7231, 1, '组长', 'leader', 'defense_group_role', 'N', '0', '1546278765432123458', NOW(), '主持并汇总结论'),
  (7232, 2, '成员', 'member', 'defense_group_role', 'Y', '0', '1546278765432123458', NOW(), '答辩组成员'),
  (7241, 1, '通过',     'pass',     'defense_result', 'Y', '0', '1546278765432123458', NOW(), '答辩通过'),
  (7242, 2, '不通过',   'fail',     'defense_result', 'N', '0', '1546278765432123458', NOW(), '答辩不通过（退回修改）'),
  (7243, 3, '缓辩',     'postpone', 'defense_result', 'N', '0', '1546278765432123458', NOW(), '缓辩（择期重排批次）'),
  (7251, 1, '优',     '优',     'defense_grade', 'N', '0', '1546278765432123458', NOW(), ''),
  (7252, 2, '良',     '良',     'defense_grade', 'N', '0', '1546278765432123458', NOW(), ''),
  (7253, 3, '中',     '中',     'defense_grade', 'N', '0', '1546278765432123458', NOW(), ''),
  (7254, 4, '及格',   '及格',   'defense_grade', 'N', '0', '1546278765432123458', NOW(), ''),
  (7255, 5, '不及格', '不及格', 'defense_grade', 'N', '0', '1546278765432123458', NOW(), '');

-- ---------- 10.4 学位类型字典（P5，检测策略集匹配维度） ----------
INSERT IGNORE INTO `sys_dict_type`
  (`id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7300, '学位类型', 'degree_type', '0', '1546278765432123458', NOW(), '论文学位类型（论文全生命周期P5，检测策略集匹配维度）');

INSERT IGNORE INTO `sys_dict_data`
  (`id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7301, 1, '本科', 'undergraduate', 'degree_type', 'Y', '0', '1546278765432123458', NOW(), '本科学位论文'),
  (7302, 2, '硕士', 'master',        'degree_type', 'N', '0', '1546278765432123458', NOW(), '硕士学位论文'),
  (7303, 3, '博士', 'doctor',        'degree_type', 'N', '0', '1546278765432123458', NOW(), '博士学位论文');

-- ---------- 10.5 导师评价标签字典（P6a） ----------
-- 学生提交时 tags 从本字典点选（可多选），后端按 dict_value 白名单校验。
-- 负向标签与正向标签同列，由前端按 remark 展示分组。
INSERT IGNORE INTO `sys_dict_type`
  (`id`, `dict_name`, `dict_type`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7400, '导师评价标签', 'advisor_eval_tag', '0', '1546278765432123458', NOW(), '学生对指导教师评价的预设标签（论文全生命周期P6a）');

INSERT IGNORE INTO `sys_dict_data`
  (`id`, `dict_sort`, `dict_label`, `dict_value`, `dict_type`, `is_default`, `status`, `create_by`, `create_time`, `remark`)
VALUES
  (7401, 1, '回复及时',   'reply_timely',       'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '正向'),
  (7402, 2, '反馈细致',   'feedback_detailed',  'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '正向'),
  (7403, 3, '要求严格',   'strict_demanding',   'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '正向'),
  (7404, 4, '因材施教',   'tailored_guidance',  'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '正向'),
  (7405, 5, '资源丰富',   'resource_support',   'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '正向'),
  (7406, 6, '指导偏少',   'guidance_lacking',   'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '负向'),
  (7407, 7, '反馈较慢',   'feedback_slow',      'advisor_eval_tag', 'N', '0', '1546278765432123458', NOW(), '负向');

-- ---------- 段 10 自检：期望 13 / 9 / 17 / 3 / 7 ----------
--     （sys_dict_data 表无 is_deleted 列，勿加此过滤）
SELECT (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type = 'paper_status')        AS paper_status_13,
       (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type IN ('review_assign_status', 'review_assign_type', 'review_assign_method')) AS review_assign_9,
       (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type IN ('defense_batch_status', 'defense_batch_type', 'defense_mode',
                     'defense_group_role', 'defense_result', 'defense_grade'))      AS defense_17,
       (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type = 'degree_type')         AS degree_type_3,
       (SELECT COUNT(*) FROM sys_dict_data WHERE dict_type = 'advisor_eval_tag')    AS advisor_eval_tag_7;


-- ============================================================================
-- 段 11：内置角色 sys_role（4 个）+ 超级管理员 sys_user（1 个）
--
-- init.sql 是纯 DDL（0 条 INSERT），段 1~7 也不管账号表，缺了本段，
-- 空库跑完系统里没有任何账号能登录、也没有任何角色可关联。
--
-- ⚠️ sys_user.email 上有唯一键 idx_sys_user_email：若库里已存在
--    admin@system.local 却挂在别的 id 上，INSERT 会报 1062，
--    先把那一行删掉或改掉邮箱再跑。
-- （原 补初始账号.sql 内容归并进本段，2026-10-12）
-- ============================================================================

-- 超级管理员ID。段 1 的 @op（各处 create_by/update_by）用的就是这个值。
SET @op := 2060661729410551809;

-- ---------- 角色 ----------
-- 只播代码真正认的 4 个：UserDetailsServiceImpl 把 role_code 直接当 GrantedAuthority，
-- 且给 SUPER_ADMIN 额外补一个 ADMIN（少了它 SUPER_ADMIN 能登录却进不了任何后台页面）。
-- 本机库里另有 SYSTEM_ADMIN / EDU_ADMIN / LAB_ASSISTANT，后端一次都没引用过，不播。
-- 幂等只靠主键 + sys_role.role_code 唯一索引 uk_role_code（存量修复.sql 段 1.13 建）；
-- 库里若已存在同 code 不同 id 的行，会先撞 uk_role_code 报 1062，请先按段 1.13 处理。
INSERT INTO `sys_role`
  (id, role_name, role_code, permissions, description,
   create_by, create_time, update_by, update_time, is_deleted, delete_by, delete_time)
VALUES
  (1001, '学生', 'STUDENT',
   '["paper:view", "paper:download", "report:submit", "report:view", "course:select", "course:view", "profile:view", "profile:edit"]',
   '普通学生角色，具有查看论文、提交报告、选课等基础权限',
   @op, NOW(), @op, NOW(), 0, NULL, NULL),
  (2001, '教师', 'TEACHER',
   '["paper:upload", "paper:view", "paper:edit", "paper:delete", "report:review", "report:view", "report:grade", "course:create", "course:edit", "course:view", "student:view", "student:manage", "profile:view", "profile:edit"]',
   '教师角色，具有上传论文、批改报告、管理课程等权限',
   @op, NOW(), @op, NOW(), 0, NULL, NULL),
  (4001, '管理员', 'ADMIN',
   '["user:manage", "role:manage", "permission:manage", "system:config", "system:monitor", "log:view", "data:backup", "profile:view", "profile:edit"]',
   '系统管理员，具有用户管理、系统配置、日志查看等权限',
   @op, NOW(), @op, NOW(), 0, NULL, NULL),
  (2060661728760434689, '超级管理员', 'SUPER_ADMIN',
   '[]',
   '系统管理员，可管理用户、配置系统、查看日志',
   @op, NOW(), @op, NOW(), 0, NULL, NULL)
ON DUPLICATE KEY UPDATE
  role_name = VALUES(role_name), role_code = VALUES(role_code),
  permissions = VALUES(permissions), description = VALUES(description),
  is_deleted = 0, update_by = @op, update_time = NOW();

-- ---------- 超级管理员 ----------
-- 密码是 BCrypt（明文 SuperAdmin@2026，只用于这一次救场，登录后立刻改掉）。
-- create_by/update_by 填自身：init.sql 该列注释写明"自注册用户填自身ID"。
INSERT INTO `sys_user`
  (id, username, password, real_name, role_id, user_type, email, email_verified, status,
   create_by, create_time, update_by, update_time, is_deleted, delete_by, delete_time)
VALUES
  (2060661729410551809, 'superadmin',
   '$2a$10$w9s9WvfO0Q14bW0l1T8fJ.Ktyf9eiB5c0LiMD/ggDLizFfnFCKtBO',
   '超级管理员', 2060661728760434689, 'SUPER_ADMIN', 'admin@system.local', 1, 1,
   2060661729410551809, NOW(), 2060661729410551809, NOW(), 0, NULL, NULL)
ON DUPLICATE KEY UPDATE
  password = VALUES(password), real_name = VALUES(real_name), role_id = VALUES(role_id),
  user_type = VALUES(user_type), email_verified = VALUES(email_verified),
  status = 1, is_deleted = 0, update_by = @op, update_time = NOW();

-- ---------- 段 11 自检：期望 roles=4 users>=1，且超管挂在 SUPER_ADMIN 上 ----------
SELECT (SELECT COUNT(*) FROM `sys_role` WHERE is_deleted = 0) AS roles,
       (SELECT COUNT(*) FROM `sys_user` WHERE is_deleted = 0) AS users;

SELECT u.id, u.username, u.user_type, r.role_code, u.status
  FROM `sys_user` u JOIN `sys_role` r ON u.role_id = r.id
 WHERE u.is_deleted = 0;
