-- ============================================================================
-- 初始账号：sys_role（4 个角色）+ 超级管理员
--
-- 为什么单独一个文件：
--   init.sql 是纯 DDL（0 条 INSERT），种子数据.sql 段 1~7 只管
--   college / major / subject / 操作类型字典 / 知识库分类 / 消息模板 / 查重规则，
--   两张账号表谁都不管。于是空库按「init.sql → 种子数据.sql」跑完，
--   系统里没有任何账号能登录、也没有任何角色可关联 —— 2026-10-08 线上就是
--   这么把一个只剩超管的库跑成 0 用户、0 模板的。
--
-- 用法：mysql -uroot -p <库名> < 补初始账号.sql
--       可重复执行（全部走 ON DUPLICATE KEY UPDATE，按主键幂等）。
--
-- ⚠️ sys_user.email 上有唯一键 idx_sys_user_email：若库里已存在
--    admin@system.local 却挂在别的 id 上，INSERT 会报 1062，
--    先把那一行删掉或改掉邮箱再跑。
-- ============================================================================

SET NAMES utf8mb4;

-- 超级管理员ID。种子数据.sql 段 1 的 @op（各处 create_by/update_by）用的就是这个值。
SET @op := 2060661729410551809;

START TRANSACTION;

-- ---------- 角色 ----------
-- 只播代码真正认的 4 个：UserDetailsServiceImpl 把 role_code 直接当 GrantedAuthority，
-- 且给 SUPER_ADMIN 额外补一个 ADMIN（少了它 SUPER_ADMIN 能登录却进不了任何后台页面）。
-- 本机库里另有 SYSTEM_ADMIN / EDU_ADMIN / LAB_ASSISTANT，后端一次都没引用过，不播。
-- 注意 sys_role.role_code 上没有唯一键（DDL 注释说"唯一"但没建），
-- 所以幂等只靠主键；库里若已存在同 code 不同 id 的行，这里会插出重复角色。
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

COMMIT;

-- ---------- 自检 ----------
SELECT (SELECT COUNT(*) FROM `sys_role` WHERE is_deleted = 0) AS roles,
       (SELECT COUNT(*) FROM `sys_user` WHERE is_deleted = 0) AS users;

SELECT u.id, u.username, u.user_type, r.role_code, u.status
  FROM `sys_user` u JOIN `sys_role` r ON u.role_id = r.id
 WHERE u.is_deleted = 0;
