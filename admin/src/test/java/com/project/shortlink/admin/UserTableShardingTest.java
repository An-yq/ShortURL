package com.project.shortlink.admin;

public class UserTableShardingTest {
    public static void main(String[] args) {
        String SQL = "create table if not exists link.t_group_%d\n" +
                "(\n" +
                "    id          bigint auto_increment comment 'ID'\n" +
                "        primary key,\n" +
                "    gid         varchar(32)  null comment '分组标识',\n" +
                "    name        varchar(64)  null comment '分组名称',\n" +
                "    username    varchar(256) null comment '创建分组用户名',\n" +
                "    sort_order  int          not null,\n" +
                "    create_time datetime     null comment '创建时间',\n" +
                "    update_time datetime     null comment '更新时间',\n" +
                "    del_flag    tinyint(1)   null comment '删除标识',\n" +
                "    constraint uni_gid_username\n" +
                "        unique (gid, username) comment 'gid和username唯一索引'\n" +
                ");";
        for (int i = 0; i < 16; i++) {
            System.out.printf(SQL,i);
        }
    }
}
