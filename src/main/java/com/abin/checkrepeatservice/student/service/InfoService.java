package com.abin.checkrepeatservice.student.service;

import com.abin.checkrepeatservice.common.Result;
import com.abin.checkrepeatservice.pojo.dto.UpdatePasswordReq;
import com.abin.checkrepeatservice.student.dto.UpdateEmailReq;
import com.abin.checkrepeatservice.student.vo.LoginHistoryVO;
import com.abin.checkrepeatservice.student.vo.LoginLogQueryReq;
import com.abin.checkrepeatservice.user.dto.UpdateUserInfoReq;
import com.abin.checkrepeatservice.user.vo.LoginVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.web.multipart.MultipartFile;

public interface InfoService {
    /**
     * 更新用户信息
     * @param updateReq 用户信息更新请求
     * @return 更新后的用户信息
     */
    Result<LoginVO> updateUserInfo(UpdateUserInfoReq updateReq);

    /**
     * 查询当前登录用户的完整信息。
     *
     * <p>前端 localStorage 只保留非敏感展示字段（见 stores/user.js 的 filterSafeInfo），
     * email/phone/introduce 等刷新后就丢了，个人中心靠这个接口补全。</p>
     *
     * @return 当前登录用户信息（结构与 updateUserInfo 的返回值一致）
     */
    Result<LoginVO> getCurrentUserInfo();

    /**
     * 上传用户头像
     * @param file 头像文件
     * @return 上传结果
     */
    Result<String> uploadAvatar(MultipartFile file);

    /**
     * 查询登录历史
     *
     * @param queryReq 查询参数
     * @return 登录历史列表
     */
    Result<Page<LoginHistoryVO>> getLoginHistory(LoginLogQueryReq queryReq);

    /**
     * 修改用户密码
     * @param updatePasswordReq 密码修改请求
     * @return 修改结果
     */
    Result<String> updatePassword(UpdatePasswordReq updatePasswordReq);

    /**
     * 发送验证邮件
     * @param email 邮箱地址
     * @return 发送结果
     */
    Result<String> sendVerifyEmail(String email);

    /**
     * 验证邮箱
     * @param token 验证令牌
     * @return 验证结果
     */
    Result<String> verifyEmail(String token);

    /**
     * 发送邮箱验证码
     * @param email 邮箱地址
     * @return 发送结果
     */
    Result<String> sendEmailCode(String email);

    /**
     * 修改用户邮箱
     * @param updateReq 修改邮箱请求
     * @return 修改结果
     */
    Result<String> updateEmail(@Valid UpdateEmailReq updateReq);
}
