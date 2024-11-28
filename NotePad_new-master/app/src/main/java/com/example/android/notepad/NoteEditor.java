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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;


import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;


/**
 * 这个Activity处理“编辑”便签，其中编辑包括响应
 * {@link Intent#ACTION_VIEW}（请求查看数据），编辑便签
 * {@link Intent#ACTION_EDIT}，创建便签 {@link Intent#ACTION_INSERT}，或者
 * 从剪贴板当前内容创建新便签 {@link Intent#ACTION_PASTE}。
 *
 * 注意：请注意，这个Activity中的提供者操作是发生在UI线程上的。
 * 这不是一个好的实践。这里只是为了使代码更易读。一个真正的
 * 应用程序应该使用 {@link android.content.AsyncQueryHandler}
 * 或 {@link android.os.AsyncTask} 对象来在单独的线程上异步执行操作。
 */
public class NoteEditor extends Activity {
    // 用于日志记录和调试目的
    private static final String TAG = "NoteEditor";

    /*
     * 创建一个投影，返回便签ID和便签内容。
     */
    private static final String[] PROJECTION =
        new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_NOTE,

    };

    // 用于保存活动的保存状态的标签
    private static final String ORIGINAL_CONTENT = "origContent";

    // 这个Activity可以通过多个动作启动。每个动作都由一个“状态”常量表示
    private static final int STATE_EDIT = 0;
    private static final int STATE_INSERT = 1;

    // 全局可变变量
    private int mState;
    private Uri mUri;
    private Cursor mCursor;
    private EditText mText;
    private String mOriginalContent;

    private String colorBack="#FFFFFF";
    private String colorText="#FFFFFF";

    public boolean isFlag=true;
    /**
     * 定义一个自定义的EditText视图，它在显示的每行文本之间绘制线条。
     */
    public static class LinedEditText extends EditText {
        private Rect mRect;
        private Paint mPaint;


        // 这是由LayoutInflater使用的构造函数
        public LinedEditText(Context context, AttributeSet attrs) {
            super(context, attrs);

            // 创建一个Rect和一个Paint对象，并设置Paint对象的风格和颜色。
            mRect = new Rect();
            mPaint = new Paint();
            mPaint.setStyle(Paint.Style.STROKE);
            mPaint.setColor(0x800000FF);
        }

        /**
         * 这个方法被调用以绘制LinedEditText对象
         * @param canvas 绘制背景的画布。
         */
        @Override
        protected void onDraw(Canvas canvas) {

            // 获取视图中的文本行数。
            int count = getLineCount();

            // 获取全局Rect和Paint对象
            Rect r = mRect;
            Paint paint = mPaint;

            /*
             * 为EditText中的每行文本在背景中绘制一条线
             */
            for (int i = 0; i < count; i++) {

                // 获取当前行文本的基线坐标
                int baseline = getLineBounds(i, r);

                /*
                 * 在背景中绘制一条线，从矩形的左侧到右侧，
                 * 在垂直位置基线下一个dip的位置，使用“paint”对象
                 * 用于详细信息。
                 */
                canvas.drawLine(r.left, baseline + 1, r.right, baseline + 1, paint);
            }

            // 最后通过调用父方法完成
            super.onDraw(canvas);
        }
    }

    /**
     * 当Activity首次启动时，Android会调用这个方法。从传入的Intent中，它确定所需的编辑类型，然后执行。
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        /*
         * 创建一个Intent，用于当Activity对象的结果返回给调用者时使用。
         */
        final Intent intent = getIntent();

        /*
         * 根据传入Intent中指定的动作设置编辑。
         */

        // 获取触发此Activity的Intent过滤器的动作
        final String action = intent.getAction();

        // 对于编辑动作：
        if (Intent.ACTION_EDIT.equals(action)) {

            // 将Activity状态设置为EDIT，并获取要编辑数据的URI。
            mState = STATE_EDIT;
            mUri = intent.getData();

            // 对于插入或粘贴动作：
        } else if (Intent.ACTION_INSERT.equals(action)
                || Intent.ACTION_PASTE.equals(action)) {

            // 将Activity状态设置为INSERT，获取一般便签URI，并在提供者中插入一个空记录
            mState = STATE_INSERT;
            mUri = getContentResolver().insert(intent.getData(), null);

            /*
             * 如果插入新便签的尝试失败，关闭此Activity。如果原始Activity请求了结果，则返回RESULT_CANCELED。
             * 日志记录插入失败。
             */
            if (mUri == null) {

                // 写入日志标识符、消息和失败的URI。
                Log.e(TAG, "Failed to insert new note into " + getIntent().getData());

                // 关闭活动。
                finish();
                return;
            }

            // 由于新条目已创建，这将结果设置为返回
            // 将结果设置为返回。
            setResult(RESULT_OK, (new Intent()).setAction(mUri.toString()));

            // 如果动作不是EDIT或INSERT：
        } else {

            // 日志记录动作不理解，关闭Activity，并返回RESULT_CANCELED给原始Activity。
            Log.e(TAG, "Unknown action, exiting");
            finish();
            return;
        }

        /*
         * 使用随触发Intent一起传递的URI，从提供者中获取便签或便签。
         * 注意：这正在UI线程上完成。它将阻塞线程，直到查询完成。在一个示例应用中，与基于本地数据库的简单提供者对抗，
         * 阻塞将是短暂的，但在真正的应用中，你应该使用
         * android.content.AsyncQueryHandler 或 android.os.AsyncTask。
         */
        mCursor = managedQuery(
                mUri,         // 从提供者获取多个便签的URI。
                PROJECTION,   // 一个投影，返回每个便签的便签ID和便签内容。
                null,         // 没有“where”子句选择标准。
                null,         // 没有“where”子句选择标准，因此不需要选择值。
                null          // 使用默认排序顺序（修改日期，降序）
        );

        // 对于粘贴，从剪贴板初始化数据。
        // （必须在mCursor初始化后执行）。
        if (Intent.ACTION_PASTE.equals(action)) {
            // 执行粘贴
            performPaste();
            // 切换到EDIT状态，以便可以修改标题。
            mState = STATE_EDIT;
        }

        // 为这个Activity设置布局。见res/layout/note_editor.xml
        setContentView(R.layout.note_editor);

        // 获取布局中的EditText句柄。
        mText = (EditText) findViewById(R.id.note);

        SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
        int backColor=sharedPreferences.getInt("backgroundColor",Color.WHITE);
        int textColor=sharedPreferences.getInt("textColor",Color.BLACK);
        mText.setBackgroundColor(backColor);
        mText.setTextColor(textColor);
        /*
         * 如果这个Activity之前已经停止过，它的状态被写入了ORIGINAL_CONTENT
         * 位置的保存实例状态中。这会获取状态。
         */
        if (savedInstanceState != null) {
            mOriginalContent = savedInstanceState.getString(ORIGINAL_CONTENT);
        }
    }

    /**
     * 当Activity即将进入前台时，这个方法会被调用。这发生在Activity来到任务栈顶部时，或者当它首次启动时。
     *
     * 移动到列表中的第一个便签，为用户选择的操作设置适当的标题，将便签内容放入TextView，并备份原始文本。
     */
    @Override
    protected void onResume() {
        super.onResume();


        if (mCursor != null) {
            // 重新查询，以防在暂停时有变化（例如标题）
            mCursor.requery();

            /* 移动到第一条记录。始终在第一次访问数据时调用moveToFirst()。使用Cursor的语义是，当它被创建时，
             * 它的内部索引指向第一条记录之前的位置。
             */
            mCursor.moveToFirst();



            // 根据当前Activity状态修改Activity的窗口标题。
            if (mState == STATE_EDIT) {
                // 将Activity的标题设置为包括便签标题
                int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                String title = mCursor.getString(colTitleIndex);
                Resources res = getResources();
                String text = String.format(res.getString(R.string.title_edit), title);
                setTitle(text);
                // 为插入设置标题为“创建”
            } else if (mState == STATE_INSERT) {
                setTitle(getText(R.string.title_create));
            }

            /*
             * onResume()可能在Activity失去焦点后被调用（被暂停）。
             * 用户在Activity暂停时正在编辑或创建便签。
             * Activity应该重新显示之前检索到的文本，但不应该移动光标位置。
             * 这有助于用户继续编辑或输入。
             */

            // 从Cursor中获取便签文本并放入TextView，但不改变文本光标的位置。
            int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
            String note = mCursor.getString(colNoteIndex);
            mText.setTextKeepState(note);

            // 存储原始便签文本，允许用户撤销更改。
            if (mOriginalContent == null) {
                mOriginalContent = note;
            }

            /*
             * 有些地方不对劲。Cursor应该始终包含数据。在便签中报告错误。
             */
        } else {
            setTitle(getText(R.string.error_title));
            mText.setText(getText(R.string.error_message));
        }
    }

    /**
     * 当Activity在正常操作中失去焦点，然后稍后被杀死时，这个方法会被调用。
     *
     * Activity有机会保存其状态，以便系统可以恢复它。
     *
     * 注意，这个方法不是Activity生命周期的正常部分。如果用户简单地从Activity导航开，它不会被调用。
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        // 保存原始文本，以便如果活动需要被杀死时，我们仍然拥有它。
        outState.putString(ORIGINAL_CONTENT, mOriginalContent);
    }

    /**
     * 当Activity失去焦点时，这个方法会被调用。
     *
     * 对于编辑信息的Activity对象，onPause()可能是保存更改的唯一地方。Android应用程序模型基于这样一个观点：“保存”和“退出”不是必需的操作。当用户导航远离Activity时，他们不应该必须回到它来完成他们的工作。离开的行为应该保存一切，并使Activity处于Android可以销毁它的状态，如果必要的话。
     *
     * 如果用户没有做任何事情，那么这将返回调用者RESULT_CANCELED的结果，并删除便签。这甚至适用于正在编辑的便签，假设是用户想要“清除”（删除）便签。
     */
    @Override
    protected void onPause() {
        super.onPause();

        /*
         * 测试查询操作没有失败（见onCreate()）。Cursor对象将存在，即使没有返回记录，除非查询因某些异常或错误而失败。
         *
         */
        if (mCursor != null) {

            // 获取当前便签文本。
            String text = mText.getText().toString();
            int length = text.length();

            /*
             * 如果Activity即将完成并且当前便签中没有文本，返回调用者RESULT_CANCELED的结果，并删除便签。这甚至适用于正在编辑的便签，假设是用户想要“清除”（删除）便签。
             */
            if (isFinishing() && (length == 0)) {
                setResult(RESULT_CANCELED);
                deleteNote();

                /*
                 * 将编辑写入提供者。如果检索到现有便签到编辑器中*或*如果插入了新便签，则已编辑便签。在后一种情况下，
                 * onCreate()在提供者中插入了一个新的空便签，并且正在编辑这个新便签。
                 */
            } else if (mState == STATE_EDIT) {
                // 创建一个映射，包含要更新到提供者的列的新值
                updateNote(text, null);
            } else if (mState == STATE_INSERT) {
                updateNote(text, text);
                mState = STATE_EDIT;
            }
        }
    }

    /**
     * 当用户第一次点击设备的菜单按钮时，这个方法会被调用。
     *
     * 为编辑和插入构建菜单，并添加注册自己来处理此应用程序MIME类型的替代操作。
     *
     * @param menu 要添加项目的菜单对象。
     * @return 显示菜单则返回True。
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // 从XML资源中填充菜单
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.editor_options_menu, menu);

        // 仅在保存的便签中添加额外的菜单项
        if (mState == STATE_EDIT) {
            // 附加到
            // 菜单项，对于任何其他可以处理它的活动也是如此。
            // 这在系统上执行查询，查找任何实现我们数据的ALTERNATIVE_ACTION的活动，为找到的每个活动添加菜单项。
            Intent intent = new Intent(null, mUri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
            menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                    new ComponentName(this, NoteEditor.class), null, intent, 0, null);
        }

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // 检查便签是否已更改并启用/禁用撤销选项
        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        String savedNote = mCursor.getString(colNoteIndex);
        String currentNote = mText.getText().toString();
        if (savedNote.equals(currentNote)) {
            menu.findItem(R.id.menu_revert).setVisible(false);
        } else {
            menu.findItem(R.id.menu_revert).setVisible(true);
        }
        return super.onPrepareOptionsMenu(menu);
    }

    /**
     * 当菜单项被选中时，这个方法会被调用。Android传入选中的项。
     * 这个方法中的switch语句调用适当的方法来执行用户选择的操作。
     *
     * @param item 被选中的MenuItem
     * @return 如果项目被处理，则返回True，不需要进一步工作。如果需要进一步处理，如MenuItem对象所示，则返回False。
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
// 处理所有可能的菜单动作。
        switch (item.getItemId()) {
            case R.id.menu_save:
                String text = mText.getText().toString();
                updateNote(text, null);
                finish();
                break;
            case R.id.menu_delete:
                deleteNote();
                finish();
                break;
            case R.id.menu_revert:
                cancelNote();
                break;
            case R.id.menu_export:
                export();
                break;
            case R.id.background_color:
                isFlag=true;
                showColor();
                break;
            case R.id.text_color:
                isFlag=false;
                showColor();
                break;

        }
        return super.onOptionsItemSelected(item);
    }

//BEGIN_INCLUDE(paste)
    /**
     * 一个辅助方法，用剪贴板的内容替换便签的数据。
     */
    private final void performPaste() {

        // 获取剪贴板管理器的句柄
        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);

        // 获取内容解析器实例
        ContentResolver cr = getContentResolver();

        // 从剪贴板获取剪贴板数据
        ClipData clip = clipboard.getPrimaryClip();
        if (clip != null) {

            String text=null;
            String title=null;

            // 获取剪贴板数据中的第一项
            ClipData.Item item = clip.getItemAt(0);

            // 尝试将项目的内容获取为指向便签的URI
            Uri uri = item.getUri();

            // 测试项目实际上是一个URI，并且该URI是一个内容URI，指向一个提供者，其MIME类型与Note pad提供者支持的MIME类型相同。
            if (uri != null && NotePad.Notes.CONTENT_ITEM_TYPE.equals(cr.getType(uri))) {

                // 剪贴板持有一个参考数据，其MIME类型为笔记类型。这将复制它。
                Cursor orig = cr.query(
                        uri,            // 内容提供者的URI
                        PROJECTION,     // 获取投影中提到的列
                        null,           // 没有选择变量
                        null,           // 没有选择变量，因此不需要标准
                        null            // 使用默认排序顺序
                );

                // 如果Cursor不为空，并且它包含至少一条记录（moveToFirst()返回true），那么这将从它中获取便签数据。
                if (orig != null) {
                    if (orig.moveToFirst()) {
                        int colNoteIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
                        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
                        text = orig.getString(colNoteIndex);
                        title = orig.getString(colTitleIndex);
                    }

                    // 关闭光标。
                    orig.close();
                }
            }

            // 如果剪贴板的内容不是对便签的引用，那么这将把它转换成文本。
            if (text == null) {
                text = item.coerceToText(this).toString();
            }

            // 使用检索到的标题和文本更新当前便签。
            updateNote(text, title);
        }

    }
//END_INCLUDE(paste)

    /**
     * 用提供的文本和标题替换当前便签内容。
     * @param text 要使用的新的便签内容。
     * @param title 要使用的新的便签标题
     */
    private final void updateNote(String text, String title) {

        // 设置一个映射，包含要在提供者中更新的值。
        ContentValues values = new ContentValues();
        long currentTimeMillis = System.currentTimeMillis();

        // 创建一个Date对象
        Date date = new Date(currentTimeMillis);

        // 创建一个SimpleDateFormat对象，指定输出格式
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        // 使用SimpleDateFormat格式化Date对象
        String formattedDate = sdf.format(date);
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,formattedDate);

        // 如果动作是插入新便签，这会为它创建一个初始标题。
        if (mState == STATE_INSERT) {

            // 如果没有提供标题作为参数，从便签文本中创建一个。
            if (title == null) {

                // 获取便签的长度
                int length = text.length();

                // 通过获取文本的子串，长度为31个字符或便签的字符数加一，以较小者为准，来设置标题。
                title = text.substring(0, Math.min(30, length));

                // 如果结果长度超过30个字符，剪掉任何尾随空格
                if (length > 30) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }
            }
            // 在值映射中设置标题的值
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {
            // 在值映射中设置标题的值
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }



        // 这将所需的便签文本放入映射中。
        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);
//
        /*
         * 使用映射中的新值更新提供者。ListView自动更新。
         * 提供者通过将查询Cursor对象的通知URI设置为传入的URI来设置这一点。
         * 内容解析器因此在Cursor对于URI更改时自动通知，UI更新。
         * 注意：这正在UI线程上完成。它将阻塞线程，直到更新完成。在一个示例应用中，与基于本地数据库的简单提供者对抗，
         * 阻塞将是短暂的，但在真正的应用中，你应该使用
         * android.content.AsyncQueryHandler 或 android.os.AsyncTask。
         */
        getContentResolver().update(
                mUri,    // 要更新的记录的URI。
                values,  // 列名和新值的映射。
                null,    // 没有选择标准，因此不需要where列。
                null     // 没有where列，因此不需要where参数。
        );


    }

    /**
     * 这个辅助方法取消对便签的工作。如果它是新创建的，则删除便签，或者恢复到便签的原始文本。
     */
    private final void cancelNote() {
        if (mCursor != null) {
            if (mState == STATE_EDIT) {
                // 将原始便签文本重新放回数据库
                mCursor.close();
                mCursor = null;
                ContentValues values = new ContentValues();
                values.put(NotePad.Notes.COLUMN_NAME_NOTE, mOriginalContent);
                getContentResolver().update(mUri, values, null, null);
            } else if (mState == STATE_INSERT) {
                // 我们插入了一个空便签，确保要删除它
                deleteNote();
            }
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    /**
     * 处理删除便签。简单地删除条目。
     */
    private final void deleteNote() {
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
            getContentResolver().delete(mUri, null, null);
            mText.setText("");
        }
    }

    private void showColor(){
        Log.d("MenuOptions", "showColor method called");
        AlertDialog alertDialog=new AlertDialog.Builder(this).setTitle("请选择颜色").
                setView(R.layout.color_layout)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create();
        alertDialog.show();
    }

    public void onClick(View v) {
        switch (v.getId()){
            case R.id.white:
                if(isFlag){
                    mText.setBackgroundColor(Color.parseColor("#FFFFFF"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorBack="#FFFFFF";
                    editor.putInt("backgroundColor",Color.parseColor(colorBack));
                    editor.apply();
                }else{
                    mText.setTextColor(Color.parseColor("#FFFFFF"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorText="#FFFFFF";
                    editor.putInt("textColor",Color.parseColor(colorText));
                    editor.apply();
                }
                break;
            case R.id.black:
                if(isFlag){
                    mText.setBackgroundColor(Color.parseColor("#000000"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorBack="#000000";
                    editor.putInt("backgroundColor",Color.parseColor(colorBack));
                    editor.apply();
                }else{
                    mText.setTextColor(Color.parseColor("#000000"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorText="#000000";
                    editor.putInt("textColor",Color.parseColor(colorText));
                    editor.apply();
                }
                break;
            case R.id.orange:
                if(isFlag){
                    mText.setBackgroundColor(Color.parseColor("#FF8C00"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorBack="#FF8C00";
                    editor.putInt("backgroundColor",Color.parseColor(colorBack));
                    editor.apply();
                }else{
                    mText.setTextColor(Color.parseColor("#FF8C00"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorText="#FF8C00";
                    editor.putInt("textColor",Color.parseColor(colorText));
                    editor.apply();
                }
                break;
            case R.id.chocolate:
                if(isFlag){
                    mText.setBackgroundColor(Color.parseColor("#D2691E"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorBack="#D2691E";
                    editor.putInt("backgroundColor",Color.parseColor(colorBack));
                    editor.apply();
                }else{
                    mText.setTextColor(Color.parseColor("#D2691E"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorText="#D2691E";
                    editor.putInt("textColor",Color.parseColor(colorText));
                    editor.apply();
                }
                break;
            case R.id.aqua:
                if(isFlag){
                    mText.setBackgroundColor(Color.parseColor("#00FFFF"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorBack="#00FFFF";
                    editor.putInt("backgroundColor",Color.parseColor(colorBack));
                    editor.apply();
                }else{
                    mText.setTextColor(Color.parseColor("#00FFFF"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorText="#00FFFF";
                    editor.putInt("textColor",Color.parseColor(colorText));
                    editor.apply();
                }
                break;
            case R.id.gray:
                if(isFlag){
                    mText.setBackgroundColor(Color.parseColor("#696969"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorBack="#696969";
                    editor.putInt("backgroundColor",Color.parseColor(colorBack));
                    editor.apply();
                }else{
                    mText.setTextColor(Color.parseColor("#696969"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorText="#696969";
                    editor.putInt("textColor",Color.parseColor(colorText));
                    editor.apply();
                }
                break;
            case R.id.pink:
                if(isFlag){
                    mText.setBackgroundColor(Color.parseColor("#D81B60"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorBack="#D81B60";
                    editor.putInt("backgroundColor",Color.parseColor(colorBack));
                    editor.apply();
                }else{
                    mText.setTextColor(Color.parseColor("#D81B60"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorText="#D81B60";
                    editor.putInt("textColor",Color.parseColor(colorText));
                    editor.apply();
                }
                break;
            case R.id.green:
                if(isFlag){
                    mText.setBackgroundColor(Color.parseColor("#00FF7F"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorBack="#00FF7F";
                    editor.putInt("backgroundColor",Color.parseColor(colorBack));
                    editor.apply();
                }else{
                    mText.setTextColor(Color.parseColor("#00FF7F"));
                    SharedPreferences sharedPreferences=getSharedPreferences("myNotesApp",MODE_PRIVATE);
                    SharedPreferences.Editor editor=sharedPreferences.edit();
                    colorText="#00FF7F";
                    editor.putInt("textColor",Color.parseColor(colorText));
                    editor.apply();
                }
                break;
        }


    }
    private static final int REQUEST_CODE_EXPORT = 100; // 100 是一个随意选定的



    private void export() {
        // 创建一个输入框
        final EditText input = new EditText(this);
        input.setHint("请输入文件名");

        // 创建对话框
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("导出笔记")
                .setView(input)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        String fileName = input.getText().toString().trim();
                        if (fileName.isEmpty()) {
                            Toast.makeText(NoteEditor.this, "文件名不能为空", Toast.LENGTH_SHORT).show();
                        } else {
                            // 启动文件选择器
                            openFilePicker(fileName);
                        }
                    }
                })
                .setNegativeButton("取消", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int which) {
                        // 取消操作的逻辑（如果有的话）
                    }
                })
                .create();

        dialog.show();
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(Color.BLACK);
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CODE_EXPORT && resultCode == RESULT_OK) {
            Uri fileUri = data.getData();

            if (fileUri != null) {
                saveNoteToFile(fileUri);
            } else {
                Toast.makeText(this, "文件创建失败", Toast.LENGTH_SHORT).show();
            }
        }
    }
    private void saveNoteToFile(Uri fileUri) {
        try {
            // 获取笔记内容
            String noteContent = mText.getText().toString();

            // 打开输出流并写入数据
            try (OutputStream outputStream = getContentResolver().openOutputStream(fileUri)) {
                if (outputStream != null) {
                    outputStream.write(noteContent.getBytes());
                    outputStream.flush();
                    Toast.makeText(this, "笔记导出成功", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(this, "无法打开文件", Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "导出失败：" + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    private void openFilePicker(String fileName) {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_TITLE, fileName + ".txt"); // 用户输入的文件名
        startActivityForResult(intent, REQUEST_CODE_EXPORT);
    }



}
