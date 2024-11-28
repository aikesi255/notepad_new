/*
 * 版权所有 (C) 2007 The Android Open Source Project
 *
 * 根据Apache License, Version 2.0（以下简称“许可证”）授权；
 * 除非遵守许可证，否则不得使用此文件。
 * 您可以在以下网址获得许可证的副本：
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * 除非适用法律要求或书面同意，否则根据许可证分发的软件是基于“原样”分发的，
 * 没有任何明示或暗示的保证或条件，包括但不限于所有权、非侵权、适销性或适用于特定目的的任何保证或条件。
 * 请参阅许可证，了解有关权限和限制的具体语言。
 */

package com.example.android.notepad;

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * 定义了便签内容提供者和其客户端之间的契约。契约定义了客户端需要访问提供者的一个或多个数据表的信息。契约是一个公共的、不可扩展的（final）类，其中包含定义列名和URI的常量。一个编写良好的客户端仅依赖于契约中的常量。
 */
public final class NotePad {
    public static final String AUTHORITY = "com.google.provider.NotePad";

    // 这个类不能被实例化
    private NotePad() {
    }

    /**
     * 便签表契约
     */
    public static final class Notes implements BaseColumns {

        // 这个类不能被实例化
        private Notes() {}

        /**
         * 此提供者提供的数据表名称
         */
        public static final String TABLE_NAME = "notes";

        /*
         * URI定义
         */

        /**
         * 此提供者的URI方案部分
         */
        private static final String SCHEME = "content://";

        /**
         * URI的路径部分
         */

        /**
         * 便签URI的路径部分
         */
        private static final String PATH_NOTES = "/notes";

        /**
         * 便签ID URI的路径部分
         */
        private static final String PATH_NOTE_ID = "/notes/";

        /**
         * 在便签ID URI的路径部分中，便签ID段的0相对位置
         */
        public static final int NOTE_ID_PATH_POSITION = 1;

        /**
         * Live Folder URI的路径部分
         */
        private static final String PATH_LIVE_FOLDER = "/live_folders/notes";

        /**
         * 此表的内容://风格URL
         */
        public static final Uri CONTENT_URI =  Uri.parse(SCHEME + AUTHORITY + PATH_NOTES);

        /**
         * 单个便签的内容URI基础。调用者必须
         * 在此Uri后追加一个数字便签id来检索一个便签
         */
        public static final Uri CONTENT_ID_URI_BASE
                = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID);

        /**
         * 通过其ID指定的单个便签的内容URI匹配模式。使用这个来匹配
         * 传入的URI或构建一个Intent。
         */
        public static final Uri CONTENT_ID_URI_PATTERN
                = Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID + "/#");

        /**
         * Live Folder便签列表的内容Uri模式
         */
        public static final Uri LIVE_FOLDER_URI
                = Uri.parse(SCHEME + AUTHORITY + PATH_LIVE_FOLDER);

        /*
         * MIME类型定义
         */

        /**
         * {@link #CONTENT_URI}提供的目录便签的MIME类型。
         */
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.google.note";

        /**
         * 单个便签的{@link #CONTENT_URI}子目录的MIME类型。
         */
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.google.note";

        /**
         * 此表的默认排序顺序
         */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";

        /*
         * 列定义
         */

        /**
         * 便签标题的列名
         * <P>类型：TEXT</P>
         */
        public static final String COLUMN_NAME_TITLE = "title";

        /**
         * 便签内容的列名
         * <P>类型：TEXT</P>
         */
        public static final String COLUMN_NAME_NOTE = "note";

        /**
         * 创建时间戳的列名
         * <P>类型：INTEGER （来自System.curentTimeMillis()的长整型）</P>
         */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /**
         * 修改时间戳的列名
         * <P>类型：INTEGER （来自System.curentTimeMillis()的长整型）</P>
         */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";


        /*
         * NoteEditor.java EditText setBackGroundColor
         * */
        public static final String COLUMN_BACKGROUND_COLOR="bColor";
        /*
         * NoteEditor.java EditText setTextColor
         * */
        public static final String COLUMN_TEXT_COLOR="tColor";


    }
}