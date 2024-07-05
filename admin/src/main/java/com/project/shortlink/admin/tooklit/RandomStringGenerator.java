package com.project.shortlink.admin.tooklit;

import java.security.SecureRandom;

/**
 * 新增短链接分组的随机gid生成器
 */
public class RandomStringGenerator {
    // 包含字母和数字的字符集
    private static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

    // SecureRandom 用于生成更安全的随机数
    private static final SecureRandom random = new SecureRandom();

    /**
     * 生成包含字母和数字的六位随机字符串
     * @return 随机字符串
     */
    public static String generateRandomString() {
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            int index = random.nextInt(CHARACTERS.length());
            sb.append(CHARACTERS.charAt(index));
        }
        return sb.toString();
    }
}
