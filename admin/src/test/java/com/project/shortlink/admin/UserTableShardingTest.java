package com.project.shortlink.admin;

public class UserTableShardingTest {
    public static void main(String[] args) {
        String SQL = "create table if not exists link.t_link_%d\n" +
                "(\n" +
                "    id              bigint auto_increment comment '短链接id'\n" +
                "        primary key,\n" +
                "    domain          varchar(128) charset utf8mb4                  null comment '域名',\n" +
                "    short_uri       varchar(8)                                    null comment '短链接',\n" +
                "    full_short_url  varchar(128) charset utf8mb4                  null comment '完整短链接',\n" +
                "    origin_url      varchar(1024) charset utf8mb4                 null comment '原始链接',\n" +
                "    click_num       int                         default 0         null comment '点击次数',\n" +
                "    gid             varchar(32) charset utf8mb4 default 'default' null comment '分组id',\n" +
                "    enable_status   tinyint(1)                                    null comment '启用状态 0：启用 1：未启用',\n" +
                "    create_type     tinyint(1)                                    null comment ' 创建类型 0：自定义 1：控制台',\n" +
                "    valid_date_type tinyint(1)                                    null comment '有效期类型 0：永久有效 1：自定义有效期',\n" +
                "    valid_date      datetime                                      null comment '有效日期',\n" +
                "    `describe`      varchar(1021) charset utf8mb4                 null comment '描述',\n" +
                "    create_time     datetime                                      null comment '创建时间',\n" +
                "    update_time     datetime                                      null comment '更新时间',\n" +
                "    del_flag        tinyint(1)                                    null comment '删除标识 0：未删除 1：已删除',\n" +
                "    favicon         varchar(256)                                  null,\n" +
                "    constraint uni_full_short_url\n" +
                "        unique (full_short_url)\n" +
                ")\n" +
                "    collate = utf8mb4_bin;";
        for (int i = 0; i < 16; i++) {
            System.out.printf(SQL,i);
        }
    }
}
