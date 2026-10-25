package com.abin.checkrepeatservice.common.service;

import com.abin.checkrepeatservice.mapper.FileInfoMapper;
import com.abin.checkrepeatservice.pojo.entity.FileInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Slf4j
public class FileService {

    @Value("${file.upload.base-path}")
    private String uploadPath;
    
    /**
     * 初始化上传路径，确保目录存在
     */
    @PostConstruct
    private void init() {
        try {
            // 如果是 Windows 环境且路径为 Unix 风格，转换为 Windows 路径
            if (System.getProperty("os.name").toLowerCase().contains("win") && 
                (uploadPath.startsWith("/") || uploadPath.startsWith("data"))) {
                // 使用用户主目录下的临时上传目录
                String userHome = System.getProperty("user.home");
                uploadPath = Paths.get(userHome, "check-repeat-service", "upload").toString();
                log.info("检测到 Windows 环境，使用上传路径：{}", uploadPath);
            }
            
            // 创建上传根目录
            File uploadDir = new File(uploadPath);
            if (!uploadDir.exists()) {
                boolean created = uploadDir.mkdirs();
                if (created) {
                    log.info("创建上传目录成功：{}", uploadPath);
                } else {
                    log.warn("创建上传目录失败：{}", uploadPath);
                }
            } else {
                log.info("上传目录已存在：{}", uploadPath);
            }
            
            // 验证目录是否可写
            if (!uploadDir.canWrite()) {
                log.error("上传目录不可写：{}", uploadPath);
                throw new RuntimeException("上传目录不可写：" + uploadPath);
            }
            
        } catch (Exception e) {
            log.error("初始化上传路径失败", e);
            throw new RuntimeException("初始化上传路径失败：" + e.getMessage());
        }
    }

    @Autowired
    private FileInfoMapper fileInfoMapper;

    // 用于异步处理的线程池
    private final ExecutorService executorService = Executors.newFixedThreadPool(3);

    // Tika实例（线程安全，可以重用）
    private final Tika tika = new Tika();

    /**
     * 上传文件（流式，兼容旧调用方）
     */
    public Long uploadFile(MultipartFile file, Long userId) {
        return uploadFile(file, userId, calculateFileMd5(file));
    }

    /**
     * 上传文件 - 流式版本。
     *
     * <p>架构修复：不再调用 {@code file.getBytes()}——原实现把整包文件读进堆，
     * 且控制器与 Service 各读一次（同一文件两份 byte[]），并发上传直接打爆 768MB 堆。
     * MD5 由调用方流式计算后传入（通常是控制器算一次、这里复用），文件本身通过
     * {@code transferTo} 直接从临时文件落到目标磁盘。</p>
     *
     * @param fileMd5 已流式计算出的文件 MD5，可为空（为空时此处再流式算一次）
     */
    public Long uploadFile(MultipartFile file, Long userId, String fileMd5) {
        String originalFilename = file.getOriginalFilename();
        log.info("开始处理文件上传 - 文件名：{}, 用户 ID: {}", originalFilename, userId);

        try {
            // 1. 生成文件 ID
            Long fileId = generateFileId();

            // 2. MD5 兜底（正常路径由控制器传入，避免重复读流）
            String md5 = (fileMd5 == null || fileMd5.isBlank())
                    ? calculateFileMd5(file) : fileMd5;

            // 3. 保存文件到磁盘（MultipartFile.transferTo，不经过堆）
            String filePath = saveFileToDisk(file, fileId, String.valueOf(userId));

            // 4. 保存文件基本信息到数据库（字数为 0，稍后异步回填）
            FileInfo fileInfo = saveBasicFileInfo(file, fileId, md5, filePath, String.valueOf(userId));

            // 5. 异步统计字数（Tika 全文解析已移出请求线程）
            asyncCountAndUpdateWords(new File(uploadPath, filePath), fileInfo);

            log.info("文件上传成功 - 文件 ID: {}, 文件名：{}, 用户 ID: {}", fileId, originalFilename, userId);
            return fileId;

        } catch (Exception e) {
            log.error("文件上传失败 - 文件名：{}, 用户 ID: {}", originalFilename, userId, e);
            throw new RuntimeException("文件上传失败：" + e.getMessage());
        }
    }

    /**
     * 异步统计字数并更新数据库（基于已落盘文件，避免占用请求线程与整包内存）
     */
    private void asyncCountAndUpdateWords(File savedFile, FileInfo fileInfo) {
        CompletableFuture.runAsync(() -> {
            try {
                int wordCount = countWordsFromFile(savedFile);

                // 更新数据库中的字数
                FileInfo updateInfo = new FileInfo();
                updateInfo.setId(fileInfo.getId());
                updateInfo.setWordCount(wordCount);
                updateInfo.setUpdateTime(LocalDateTime.now());

                fileInfoMapper.updateById(updateInfo);

                log.info("异步字数统计完成 - 文件 ID: {}, 文件名: {}, 字数: {}",
                        fileInfo.getId(), fileInfo.getOriginalFilename(), wordCount);

            } catch (Exception e) {
                log.warn("异步字数统计失败 - 文件 ID: {}, 文件名: {}",
                        fileInfo.getId(), fileInfo.getOriginalFilename(), e);

                // 失败时设置字数为0
                FileInfo updateInfo = new FileInfo();
                updateInfo.setId(fileInfo.getId());
                updateInfo.setWordCount(0);
                fileInfoMapper.updateById(updateInfo);
            }
        }, executorService);
    }

    /**
     * 生成文件 ID
     */
    private Long generateFileId() {
        return System.currentTimeMillis();
    }

    /**
     * 流式计算公式文件 MD5。
     *
     * <p>不把文件读进内存：用 8KB 缓冲区边读边摘要，50MB 论文也只占用一个缓冲区。
     * 计算失败直接抛错，不再返回伪造的 {@code md5_<时间戳>}——那会让秒传与完整性校验
     * 静默失效。</p>
     */
    public String calculateFileMd5(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream();
             DigestInputStream digestInputStream = new DigestInputStream(
                     inputStream, MessageDigest.getInstance("MD5"))) {
            byte[] buffer = new byte[8192];
            while (digestInputStream.read(buffer) != -1) {
                // 只做摘要，不保留内容
            }
            return HexFormat.of().formatHex(digestInputStream.getMessageDigest().digest());
        } catch (Exception e) {
            log.error("计算文件MD5失败 - 文件名：{}", file.getOriginalFilename(), e);
            throw new RuntimeException("文件MD5计算失败：" + e.getMessage(), e);
        }
    }


    /**
     * 保存文件到磁盘
     */
    private String saveFileToDisk(MultipartFile file, Long fileId, String userId) throws IOException {
        // 生成文件存储路径
        String datePath = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
    
        // 清理文件名中的非法字符
        String safeFilename = file.getOriginalFilename()
                .replaceAll("[<>:\"|?*]", "_") // Windows 非法字符
                .replaceAll("[/\\\\]", "_");   // 路径分隔符
    
        String fileName = fileId + "_" + safeFilename;
        String relativePath = Paths.get(userId, datePath, fileName).toString();

        // 阶段 5 安全加固：规范化后必须仍位于上传根目录内，阻断 ../ 路径穿越
        Path root = Paths.get(uploadPath).toAbsolutePath().normalize();
        Path target = root.resolve(relativePath).normalize();
        if (!target.startsWith(root)) {
            throw new IOException("非法的文件存储路径");
        }
        File destFile = target.toFile();
        File parentDir = destFile.getParentFile();
        if (parentDir != null && !parentDir.exists()) {
            boolean created = parentDir.mkdirs();
            if (!created) {
                log.error("创建目录失败: {}", parentDir.getAbsolutePath());
                throw new IOException("创建目录失败: " + parentDir.getAbsolutePath());
            }
            log.debug("创建目录成功: {}", parentDir.getAbsolutePath());
        }

        // 保存文件
        file.transferTo(destFile);
        log.debug("文件保存到磁盘成功: {}", destFile.getAbsolutePath());

        return relativePath;
    }

    /**
     * 保存基本文件信息到数据库（不包含字数统计）
     */
    private FileInfo saveBasicFileInfo(MultipartFile file, Long fileId, String fileMd5,
                                       String filePath, String userId) throws IOException {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setId(fileId);
        fileInfo.setOriginalFilename(file.getOriginalFilename());
        fileInfo.setFileSize(file.getSize());
        fileInfo.setFileSizeDesc(formatFileSize(file.getSize()));
        fileInfo.setStoragePath(filePath);
        fileInfo.setMd5(fileMd5);
        fileInfo.setUploadTime(LocalDateTime.now());
        fileInfo.setUploadUserId(userId);

        // 初始化字数为0，异步更新
        fileInfo.setWordCount(0);
        // file_info.create_time 是 `DEFAULT NULL`（没有默认值），不能像 SysNotice 那样交给库填充
        fileInfo.setCreateTime(LocalDateTime.now());
        fileInfo.setUpdateTime(LocalDateTime.now());

        fileInfoMapper.insert(fileInfo);
        // 字数统一由 asyncCountAndUpdateWords 在后台回填。
        return fileInfo;
    }
    /**
     * 从已保存的文件中统计字数
     */
    private int countWordsFromFile(File savedFile) throws IOException {
        try {
            // 使用 Apache Tika 提取文本内容
            org.apache.tika.Tika tika = new org.apache.tika.Tika();

            // 从已保存的文件中提取文本内容
            String content = tika.parseToString(savedFile);

            // 如果内容为空，返回0
            if (content == null || content.trim().isEmpty()) {
                return 0;
            }

            // 统计字数
            return countChineseAndEnglish(content.trim());

        } catch (Exception e) {
            log.warn("文件字数统计失败 - 文件路径: {}", savedFile.getAbsolutePath(), e);
            return 0;
        }
    }

    /**
     * 中文按字符统计，英文按单词统计 - 优化版本
     */
    private int countChineseAndEnglish(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }

        int count = 0;

        // 更准确的分割方式，考虑中英文混合
        String[] segments = text.split("(?<=[\\p{Punct}\\s])|(?=[\\p{Punct}\\s])");

        for (String segment : segments) {
            if (segment.trim().isEmpty()) {
                continue;
            }

            // 判断是否为纯中文片段
            if (isChineseSegment(segment)) {
                // 中文字符数
                for (char c : segment.toCharArray()) {
                    if (isChineseChar(c)) {
                        count++;
                    }
                }
            } else if (segment.matches("[a-zA-Z]+")) {
                // 纯英文单词
                count++;
            } else if (segment.matches("[a-zA-Z0-9]+")) {
                // 英文单词或数字
                count++;
            }
            // 标点符号和空白字符不计入字数
        }

        return count;
    }

    /**
     * 判断是否为中文片段
     */
    private boolean isChineseSegment(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }

        int chineseCount = 0;
        int totalCount = 0;

        for (char c : str.toCharArray()) {
            if (!Character.isWhitespace(c)) {
                totalCount++;
                if (isChineseChar(c)) {
                    chineseCount++;
                }
            }
        }

        // 如果超过50%的字符是中文，认为是中文片段
        return totalCount > 0 && (chineseCount * 2 >= totalCount);
    }

    /**
     * 判断字符是否为中文字符（扩展范围）
     */
    private boolean isChineseChar(char c) {
        // Unicode中的CJK统一表意文字范围（包括扩展区）
        return (c >= 0x4E00 && c <= 0x9FFF) ||      // 基本汉字
                (c >= 0x3400 && c <= 0x4DBF) ||      // 扩展A
                (c >= 0x20000 && c <= 0x2A6DF) ||    // 扩展B（需要代理对处理）
                (c >= 0x2A700 && c <= 0x2B73F) ||    // 扩展C
                (c >= 0x2B740 && c <= 0x2B81F) ||    // 扩展D
                (c >= 0x2B820 && c <= 0x2CEAF) ||    // 扩展E
                (c >= 0x2CEB0 && c <= 0x2EBEF) ||    // 扩展F
                (c >= 0x30000 && c <= 0x3134F);      // 扩展G
    }

    /**
     * 格式化文件大小
     */
    private String formatFileSize(long size) {
        if (size < 1024) {
            return size + "B";
        }

        double kbSize = size / 1024.0;
        if (kbSize < 1024) {
            return String.format("%.1fKB", kbSize);
        }

        double mbSize = kbSize / 1024.0;
        if (mbSize < 1024) {
            return String.format("%.1fMB", mbSize);
        }

        double gbSize = mbSize / 1024.0;
        return String.format("%.1fGB", gbSize);
    }

    /**
     * 根据文件 ID 获取文件信息
     */
    public FileInfo getById(Long fileId) {
        return fileInfoMapper.selectById(fileId);
    }

    /**
     * 按库里的相对存储路径解析为磁盘文件。
     *
     * <p>与上传/删除共用同一 {@code uploadPath}（含 Windows 开发环境的路径重写），
     * 避免其它服务各自拼路径导致本地能跑、线上找不到文件。</p>
     */
    public File resolveStoredFile(String storagePath) {
        if (storagePath == null || storagePath.isBlank()) {
            return null;
        }
        return new File(uploadPath, storagePath);
    }

    /**
     * 根据 MD5 查找任意一条文件记录。
     *
     * <p>秒传共享物理存储后，同一 md5 可能对应多条 file_info 记录，
     * 必须加 LIMIT 1，否则 selectOne 会抛 TooManyResultsException。</p>
     */
    public FileInfo getByMd5(String md5) {
        if (md5 == null || md5.isBlank()) {
            return null;
        }
        return fileInfoMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getMd5, md5)
                        .orderByDesc(FileInfo::getUploadTime)
                        .last("LIMIT 1"));
    }

    /**
     * 根据 MD5 + 上传人查找文件（秒传探测只允许命中自己的文件）
     */
    public FileInfo getByMd5AndUser(String md5, Long userId) {
        if (md5 == null || md5.isBlank() || userId == null) {
            return null;
        }
        return fileInfoMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileInfo>()
                        .eq(FileInfo::getMd5, md5)
                        .eq(FileInfo::getUploadUserId, String.valueOf(userId))
                        .orderByDesc(FileInfo::getUploadTime)
                        .last("LIMIT 1"));
    }

    /**
     * 秒传命中他人文件时，创建一条归属于当前用户的逻辑记录。
     *
     * <p>安全修复（P0）：不允许把他人 fileId 直接返回给当前用户（等价于越权下载权），
     * 但物理文件已经存在，无需重复写盘——新记录复用同一 storagePath，仅隔离归属。</p>
     */
    public FileInfo createSharedRecord(FileInfo source, Long userId) {
        if (source == null || userId == null) {
            throw new IllegalArgumentException("创建共享文件记录失败：来源文件或用户为空");
        }
        FileInfo shared = new FileInfo();
        shared.setOriginalFilename(source.getOriginalFilename());
        shared.setFileSize(source.getFileSize());
        shared.setFileSizeDesc(source.getFileSizeDesc());
        shared.setStoragePath(source.getStoragePath());
        shared.setMd5(source.getMd5());
        shared.setWordCount(source.getWordCount() == null ? 0 : source.getWordCount());
        shared.setUploadTime(LocalDateTime.now());
        shared.setUploadUserId(String.valueOf(userId));
        shared.setRemark("秒传复用：物理文件与 fileId=" + source.getId() + " 共享");
        shared.setCreateTime(LocalDateTime.now());
        shared.setUpdateTime(LocalDateTime.now());
        // id 由 @TableId(type = ASSIGN_ID) 生成，避免再撞时间戳主键
        fileInfoMapper.insert(shared);
        return shared;
    }

    /**
     * 删除文件。
     *
     * <p>秒传会让多条记录共享同一个 storagePath，因此必须先删记录、再确认没有其他
     * 引用，最后才删除物理文件；否则删掉一条记录会连带删掉别人正在用的文件。</p>
     */
    public boolean deleteFile(Long fileId) {
        try {
            FileInfo fileInfo = getById(fileId);
            if (fileInfo == null) {
                return false;
            }
            // 先删数据库记录
            int rows = fileInfoMapper.deleteById(fileId);
            if (rows <= 0) {
                return false;
            }
            log.debug("数据库记录删除成功 - 文件 ID: {}", fileId);

            // 仍被其他记录引用时保留物理文件（共享存储）
            Long refCount = fileInfoMapper.selectCount(
                    new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FileInfo>()
                            .eq(FileInfo::getStoragePath, fileInfo.getStoragePath()));
            if (refCount != null && refCount > 0) {
                log.debug("物理文件仍被 {} 条记录引用，跳过删除：{}", refCount, fileInfo.getStoragePath());
                return true;
            }

            String fullPath = Paths.get(uploadPath, fileInfo.getStoragePath()).toString();
            boolean deleted = Files.deleteIfExists(Paths.get(fullPath));
            if (deleted) {
                log.debug("磁盘文件删除成功：{}", fullPath);
            }
            return true;
        } catch (Exception e) {
            log.error("删除文件失败 - 文件 ID: {}", fileId, e);
            return false;
        }
    }
}