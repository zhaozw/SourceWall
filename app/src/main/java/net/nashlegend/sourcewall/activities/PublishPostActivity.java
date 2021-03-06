package net.nashlegend.sourcewall.activities;

import static android.view.inputmethod.InputMethodManager.HIDE_NOT_ALWAYS;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.v7.widget.Toolbar;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.style.ImageSpan;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import net.nashlegend.sourcewall.R;
import net.nashlegend.sourcewall.data.Consts.Extras;
import net.nashlegend.sourcewall.data.Consts.Keys;
import net.nashlegend.sourcewall.data.Consts.RequestCode;
import net.nashlegend.sourcewall.data.Mob;
import net.nashlegend.sourcewall.data.database.GroupHelper;
import net.nashlegend.sourcewall.data.database.gen.MyGroup;
import net.nashlegend.sourcewall.dialogs.InputDialog;
import net.nashlegend.sourcewall.events.Emitter;
import net.nashlegend.sourcewall.events.GroupFetchedEvent;
import net.nashlegend.sourcewall.fragment.PostPagerFragment;
import net.nashlegend.sourcewall.model.PrepareData;
import net.nashlegend.sourcewall.model.SubItem;
import net.nashlegend.sourcewall.request.NetworkTask;
import net.nashlegend.sourcewall.request.Param;
import net.nashlegend.sourcewall.request.ResponseObject;
import net.nashlegend.sourcewall.request.SimpleCallBack;
import net.nashlegend.sourcewall.request.api.APIBase;
import net.nashlegend.sourcewall.request.api.PostAPI;
import net.nashlegend.sourcewall.util.DisplayUtil;
import net.nashlegend.sourcewall.util.ErrorUtils;
import net.nashlegend.sourcewall.util.FileUtil;
import net.nashlegend.sourcewall.util.PrefsUtil;
import net.nashlegend.sourcewall.util.SketchUtil;
import net.nashlegend.sourcewall.util.UiUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import rx.Observable;
import rx.Observer;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.functions.Func1;
import rx.schedulers.Schedulers;


/**
 * 发贴，Markdown格式
 */
public class PublishPostActivity extends BaseActivity implements View.OnClickListener {
    private EditText titleEditText;
    private EditText bodyEditText;
    private ImageButton imgButton;
    private ImageButton insertButton;
    private TextView groupSpinner;
    private TextView topicSpinner;
    private View uploadingProgress;
    private ProgressDialog progressDialog;
    private String tmpImagePath;
    private SubItem subItem;
    private String group_id = "";
    private String csrf = "";
    private String topic = "";
    private int topicIndex = -1;
    private final ArrayList<Param> topics = new ArrayList<>();
    private final List<SubItem> subItems = new ArrayList<>();
    private boolean replyOK;
    private CheckBox checkBox;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_publish_post);
        Toolbar toolbar = (Toolbar) findViewById(R.id.action_bar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        titleEditText = (EditText) findViewById(R.id.text_post_title);
        bodyEditText = (EditText) findViewById(R.id.text_post_body);
        groupSpinner = (TextView) findViewById(R.id.spinner_group);
        topicSpinner = (TextView) findViewById(R.id.spinner_post_topic);
        ImageButton publishButton = (ImageButton) findViewById(R.id.btn_publish);
        imgButton = (ImageButton) findViewById(R.id.btn_add_img);
        insertButton = (ImageButton) findViewById(R.id.btn_insert_img);
        ImageButton linkButton = (ImageButton) findViewById(R.id.btn_link);
        uploadingProgress = findViewById(R.id.prg_uploading_img);
        SubItem tmpSubItem = getIntent().getParcelableExtra(Extras.Extra_SubItem);
        if (tmpSubItem != null) {
            setGroup(tmpSubItem);
        }
        prepareGroups();
        publishButton.setOnClickListener(this);
        imgButton.setOnClickListener(this);
        insertButton.setOnClickListener(this);
        linkButton.setOnClickListener(this);
        groupSpinner.setOnClickListener(this);
        topicSpinner.setOnClickListener(this);
    }

    private void setGroup(SubItem item) {
        if (item == null) {
            return;
        }
        groupSpinner.setText(item.getName());
        groupSpinner.setVisibility(View.VISIBLE);
        if (this.subItem != null && equals(this.subItem.getValue(), item.getValue())) {
            return;
        }
        this.subItem = item;
        group_id = subItem.getValue();
        csrf = "";
        topic = "";
        topics.clear();
        titleEditText.setHint(R.string.hint_input_post_title);
        bodyEditText.setHint(R.string.hint_input_post_content);
        prepare();
        tryRestoreReply();
    }

    private void setTopic(int i) {
        if (i < topics.size()) {
            topicSpinner.setText(topics.get(i).key);
            topicIndex = i;
            topicSpinner.setVisibility(View.VISIBLE);
        }
    }

    private void prepareGroups() {
        if (this.subItem == null) {
            topicSpinner.setVisibility(View.GONE);
        }
        List<SubItem> groupSubItems = GroupHelper.getAllMyGroupSubItems();
        if (groupSubItems == null || groupSubItems.size() == 0) {
            if (subItem == null) {
                final Subscription subscription = PostAPI
                        .getAllMyGroupsAndMerge()
                        .flatMap(new Func1<ArrayList<MyGroup>, Observable<List<SubItem>>>() {
                            @Override
                            public Observable<List<SubItem>> call(
                                    ArrayList<MyGroup> responseObject) {
                                if (responseObject.size() > 0) {
                                    return Observable.just(GroupHelper.getAllMyGroupSubItems());
                                } else {
                                    return Observable.error(
                                            new IllegalStateException("No Data Received"));
                                }
                            }
                        })
                        .subscribeOn(Schedulers.io())
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(new Observer<List<SubItem>>() {
                            @Override
                            public void onCompleted() {
                                UiUtil.dismissDialog(progressDialog);
                            }

                            @Override
                            public void onError(Throwable e) {
                                UiUtil.dismissDialog(progressDialog);
                                finish();
                            }

                            @Override
                            public void onNext(List<SubItem> myGroups) {
                                UiUtil.dismissDialog(progressDialog);
                                onGetGroups(myGroups);
                                PostPagerFragment.shouldNotifyDataSetChanged = true;
                                Emitter.emit(new GroupFetchedEvent());
                            }
                        });
                UiUtil.dismissDialog(progressDialog);
                progressDialog = new ProgressDialog(PublishPostActivity.this);
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setMessage(getString(R.string.message_wait_a_minute));
                progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        if (!subscription.isUnsubscribed()) {
                            subscription.unsubscribe();
                        }
                        finish();
                    }
                });
                progressDialog.show();
            } else {
                groupSpinner.setVisibility(View.GONE);
            }
            return;
        }
        onGetGroups(groupSubItems);
    }

    private void onGetGroups(List<SubItem> groupSubItems) {
        if (groupSubItems == null || groupSubItems.size() == 0) {
            return;
        }
        this.subItems.clear();
        this.subItems.addAll(groupSubItems);
        if (this.subItem == null) {
            setGroup(subItems.get(0));
        } else {
            for (int i = 0; i < groupSubItems.size(); i++) {
                SubItem subItem = groupSubItems.get(i);
                if (equals(subItem.getValue(), this.subItem.getValue())) {
                    setGroup(subItems.get(i));
                    break;
                }
            }
        }
    }


    public static boolean equals(Object a, Object b) {
        return (a == null) ? (b == null) : a.equals(b);
    }

    private void tryRestoreReply() {
        if (subItem == null) {
            return;
        }
        String sketchTitle = "";
        String sketchContent = "";
        sketchTitle = SketchUtil.readString(
                Keys.Key_Sketch_Publish_Post_Title + "_" + subItem.getValue(), "");
        sketchContent = SketchUtil.readString(
                Keys.Key_Sketch_Publish_Post_Content + "_" + subItem.getValue(), "");
        titleEditText.setText(sketchTitle);
        bodyEditText.setText(restore2Spanned(sketchContent));
    }

    public SpannableString restore2Spanned(String str) {
        SpannableString spanned = new SpannableString(str);
        String regImageAndLinkString =
                "(\\!\\[[^\\]]*?\\]\\((.*?)\\))|(\\[([^\\]]*?)\\]\\((.*?)\\))";
        Matcher matcher = Pattern.compile(regImageAndLinkString).matcher(str);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            //matcher.groupCount()==5;所以最多可以matcher.group(5)
            //matcher.group(0)表示匹配到的字符串;可能是图片链接字符串

            //matcher.group(1)表示匹配到的图片链接字符串;
            //matcher.group(2)表示匹配到的图片链接;

            //matcher.group(3)表示匹配到的超链接字符串;
            //matcher.group(4)表示匹配到的超链接标题字符串;
            //matcher.group(5)表示匹配到的超链接地址字符串;
            if (!TextUtils.isEmpty(matcher.group(1))) {
                //String imageUrl = matcher.group(2);
                Bitmap sourceBitmap = BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_txt_image_16dp);
                ImageSpan imageSpan = getImageSpan("图片链接...", sourceBitmap);
                spanned.setSpan(imageSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            } else {
                String linkTitle = matcher.group(4);
                String linkUrl = matcher.group(5);
                if (!linkUrl.startsWith("http")) {
                    linkUrl = "http://" + linkUrl;
                }
                Bitmap sourceBitmap = BitmapFactory.decodeResource(getResources(),
                        R.drawable.ic_link_16dp);
                String displayed;
                if (TextUtils.isEmpty(linkTitle.trim())) {
                    Uri uri = Uri.parse(linkUrl);
                    displayed = uri.getHost();
                    if (TextUtils.isEmpty(displayed)) {
                        displayed = "网络地址";
                    }
                    displayed += "...";
                } else {
                    displayed = linkTitle;
                }
                ImageSpan imageSpan = getImageSpan(displayed, sourceBitmap);
                spanned.setSpan(imageSpan, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return spanned;
    }

    private ImageSpan getImageSpan(String displayed, Bitmap sourceBitmap) {

        int size = (int) bodyEditText.getTextSize();
        int height = bodyEditText.getLineHeight();

        //根据要绘制的文字计算bitmap的宽度
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.BLUE);
        textPaint.setTextSize(size);
        float textFrom = (float) (size * 1.5);
        float textEndSpan = (float) (size * 0.3);
        float totalWidth = textPaint.measureText(displayed);

        //生成对应尺寸的bitmap
        Bitmap bitmap = Bitmap.createBitmap((int) (totalWidth + textFrom + textEndSpan), height,
                Bitmap.Config.ARGB_8888);

        //缩放sourceBitmap
        Matrix matrix = new Matrix();
        float scale = size / sourceBitmap.getWidth();
        matrix.setScale(scale, scale);
        matrix.postTranslate((height - size) / 2, (height - size) / 2);

        Canvas canvas = new Canvas(bitmap);

        //画背景
        Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        bgPaint.setStyle(Paint.Style.FILL);
        bgPaint.setColor(Color.parseColor("#009699"));
        canvas.drawRect(0f, 0f, bitmap.getWidth(), bitmap.getHeight(), bgPaint);

        //画图标
        Paint paint = new Paint();
        canvas.drawBitmap(sourceBitmap, matrix, paint);

        //画文字
        float actualHeight = textPaint.getFontMetrics().descent - textPaint.getFontMetrics().ascent;
        float line = (height - actualHeight) / 2 - textPaint.getFontMetrics().ascent;
        canvas.drawText(displayed, textFrom, line, textPaint);

        return new ImageSpan(this, bitmap, ImageSpan.ALIGN_BOTTOM);
    }

    private void tryClearSketch() {
        SketchUtil.remove(Keys.Key_Sketch_Publish_Post_Content + "_" + subItem.getValue());
        SketchUtil.remove(Keys.Key_Sketch_Publish_Post_Title + "_" + subItem.getValue());
    }

    private void saveSketch() {
        if (!replyOK && subItem != null) {
            if (!TextUtils.isEmpty(titleEditText.getText().toString().trim()) || !TextUtils.isEmpty(
                    bodyEditText.getText().toString().trim())) {
                String sketchTitle = titleEditText.getText().toString();
                String sketchContent = bodyEditText.getText().toString();
                SketchUtil.saveString(Keys.Key_Sketch_Publish_Post_Title + "_" + subItem.getValue(),
                        sketchTitle);
                SketchUtil.saveString(
                        Keys.Key_Sketch_Publish_Post_Content + "_" + subItem.getValue(),
                        sketchContent);
            } else if (TextUtils.isEmpty(titleEditText.getText().toString().trim())
                    && TextUtils.isEmpty(bodyEditText.getText().toString().trim())) {
                tryClearSketch();
            }
        }
    }

    @Override
    protected void onDestroy() {
        saveSketch();
        super.onDestroy();
    }

    private void prepare() {
        SimpleCallBack<PrepareData> callBack = new SimpleCallBack<PrepareData>() {
            @Override
            public void onFailure(@NonNull ResponseObject<PrepareData> result) {
                if (result.statusCode == 403) {
                    new AlertDialog.Builder(PublishPostActivity.this)
                            .setTitle(R.string.hint)
                            .setMessage(getString(R.string.have_not_join_this_group))
                            .setPositiveButton(R.string.ok, null)
                            .create()
                            .show();
                } else {
                    new AlertDialog.Builder(PublishPostActivity.this)
                            .setTitle(getString(R.string.get_csrf_failed))
                            .setMessage(getString(R.string.hint_reload_csrf))
                            .setPositiveButton(R.string.ok, null)
                            .create()
                            .show();
                }
            }

            @Override
            public void onSuccess(@NonNull PrepareData result) {
                toast(getString(R.string.get_csrf_ok));
                onReceivePreparedData(result);
            }
        };
        PostAPI.getPostPrepareData(group_id, callBack);
    }

    private void onReceivePreparedData(PrepareData prepareData) {
        csrf = prepareData.getCsrf();
        topics.clear();
        topics.addAll(prepareData.getPairs());
        setTopic(0);
    }

    private void invokeImageDialog() {
        String[] ways = {getString(R.string.add_image_from_disk), getString(
                R.string.add_image_from_camera), getString(R.string.add_image_from_link)};
        new AlertDialog.Builder(this)
                .setTitle(R.string.way_to_add_image)
                .setItems(ways, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which) {
                            case 0:
                                Intent intent = new Intent();
                                intent.setType("image/*");
                                intent.setAction(Intent.ACTION_GET_CONTENT);
                                startOneActivityForResult(intent,
                                        RequestCode.Code_Invoke_Image_Selector);
                                break;
                            case 1:
                                invokeCamera();
                                break;
                            case 2:
                                invokeImageUrlDialog();
                                break;
                        }
                    }
                })
                .create()
                .show();
    }

    private String getPossibleUrlFromClipBoard() {
        ClipboardManager manager = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        ClipData clip = manager.getPrimaryClip();
        String chars = "";
        if (clip != null && clip.getItemCount() > 0) {
            String tmpChars = (clip.getItemAt(0).coerceToText(this).toString()).trim();
            if (tmpChars.startsWith("http://") || tmpChars.startsWith("https://")) {
                chars = tmpChars;
            }
        }
        return chars;
    }

    private void invokeImageUrlDialog() {
        InputDialog.Builder builder = new InputDialog.Builder(this);
        builder.setTitle(R.string.input_image_url);
        builder.setCancelable(true);
        builder.setCanceledOnTouchOutside(false);
        builder.setSingleLine();
        builder.setInputText(getPossibleUrlFromClipBoard());
        builder.setOnClickListener(new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    InputDialog d = (InputDialog) dialog;
                    String text = d.InputString;
                    insertImagePath(text.trim());
                }
            }
        });
        InputDialog inputDialog = builder.create();
        inputDialog.show();
    }

    public void uploadImage(String path) {
        if (!FileUtil.isImage(path)) {
            toast(R.string.file_not_image);
            return;
        }
        if (!new File(path).exists()) {
            toast(R.string.file_not_exists);
        }
        if (!PrefsUtil.readBoolean(Keys.Key_User_Has_Learned_Add_Image, false)) {
            new AlertDialog.Builder(PublishPostActivity.this)
                    .setTitle(R.string.hint)
                    .setMessage(R.string.tip_of_user_learn_add_image)
                    .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            PrefsUtil.saveBoolean(Keys.Key_User_Has_Learned_Add_Image, true);
                        }
                    }).create().show();
        }
        setImageButtonsUploading();
        APIBase.uploadImage(path, new SimpleCallBack<String>() {
            @Override
            public void onFailure() {
                resetImageButtons();
                toast(R.string.upload_failed);
            }

            @Override
            public void onSuccess(@NonNull String result) {
                toast(R.string.hint_click_to_add_image_to_editor);
                doneUploadingImage(result);
                if (tmpUploadFile != null && tmpUploadFile.exists()) {
                    tmpUploadFile.delete();
                }
            }
        });
    }

    private void doneUploadingImage(String url) {
        // tap to insert image
        tmpImagePath = url;
        setImageButtonsPrepared();
    }

    /**
     * 插入图片
     */
    private void insertImagePath(String url) {
        if (TextUtils.isEmpty(url)) {
            return;
        }
        String imgTag = "![](" + url + ")";
        SpannableString spanned = new SpannableString(imgTag);
        Bitmap sourceBitmap = BitmapFactory.decodeResource(getResources(),
                R.drawable.ic_txt_image_16dp);
        String displayed = "图片链接...";
        ImageSpan imageSpan = getImageSpan(displayed, sourceBitmap);
        spanned.setSpan(imageSpan, 0, imgTag.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        int start = bodyEditText.getSelectionStart();
        bodyEditText.getText().insert(start, " ").insert(start + 1, spanned).insert(
                start + 1 + imgTag.length(), " ");
        resetImageButtons();
    }

    File tmpUploadFile = null;

    private void invokeCamera() {
        String parentPath;
        File pFile = null;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            pFile = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        }
        if (pFile == null) {
            pFile = getFilesDir();
        }
        parentPath = pFile.getAbsolutePath();
        tmpUploadFile = new File(parentPath, System.currentTimeMillis() + ".jpg");
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        Uri localUri = Uri.fromFile(tmpUploadFile);
        intent.putExtra(MediaStore.EXTRA_OUTPUT, localUri);
        startOneActivityForResult(intent, RequestCode.Code_Invoke_Camera);
    }

    /**
     * 插入链接
     */
    private void insertLink() {
        InputDialog.Builder builder = new InputDialog.Builder(this);
        builder.setTitle(R.string.input_link_url);
        builder.setCancelable(true);
        builder.setCanceledOnTouchOutside(false);
        builder.setTwoLine();
        builder.setInputText(getPossibleUrlFromClipBoard());
        builder.setOnClickListener(new DialogInterface.OnClickListener() {

            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    InputDialog d = (InputDialog) dialog;
                    String url = d.InputString;
                    if (!url.startsWith("http")) {
                        url = "http://" + url;
                    }
                    String title = d.InputString2;
                    String result = "[" + title + "](" + url + ")";

                    SpannableString spanned = new SpannableString(result);
                    Bitmap sourceBitmap = BitmapFactory.decodeResource(getResources(),
                            R.drawable.ic_link_16dp);
                    String displayed;
                    if (TextUtils.isEmpty(title.trim())) {
                        Uri uri = Uri.parse(url);
                        displayed = uri.getHost();
                        if (TextUtils.isEmpty(displayed)) {
                            displayed = "网络地址";
                        }
                        displayed += "...";
                    } else {
                        displayed = title;
                    }
                    ImageSpan imageSpan = getImageSpan(displayed, sourceBitmap);
                    spanned.setSpan(imageSpan, 0, result.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    int start = bodyEditText.getSelectionStart();
                    bodyEditText.getText().insert(start, " ").insert(start + 1, spanned).insert(
                            start + 1 + result.length(), " ");
                }
            }
        });
        InputDialog inputDialog = builder.create();
        inputDialog.show();
    }

    private void publish() {
        if (TextUtils.isEmpty(titleEditText.getText().toString().trim())) {
            toast(R.string.title_cannot_be_empty);
            return;
        }

        if (TextUtils.isEmpty(bodyEditText.getText().toString().trim())) {
            toast(R.string.content_cannot_be_empty);
            return;
        }

        if (TextUtils.isEmpty(csrf)) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.hint)
                    .setMessage(R.string.validate_failed_try_again)
                    .setPositiveButton(R.string.ok, null)
                    .show();
            return;
        }

        topic = topics.get(topicIndex).value;
        hideInput();
        String title = titleEditText.getText().toString();
        String body = bodyEditText.getText().toString();
        publishPost(group_id, csrf, title, body, topic);
        Mob.onEvent(Mob.Event_Publish_Post);
    }

    private void hideInput() {
        try {
            if (getCurrentFocus() != null) {
                ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
                        .hideSoftInputFromWindow(getCurrentFocus().getWindowToken(),
                                HIDE_NOT_ALWAYS);
            }
        } catch (Exception e) {
            ErrorUtils.onException(e);
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_publish:
                publish();
                break;
            case R.id.btn_add_img:
                invokeImageDialog();
                break;
            case R.id.btn_insert_img:
                insertImagePath(tmpImagePath);
                break;
            case R.id.btn_link:
                insertLink();
                break;
            case R.id.spinner_group:
                popGroup();
                break;
            case R.id.spinner_post_topic:
                popTopic();
                break;
        }
    }

    private void popGroup() {
        if (subItems.size() == 0) {
            return;
        }
        String[] items = new String[subItems.size()];
        for (int i = 0; i < subItems.size(); i++) {
            items[i] = subItems.get(i).getName();
        }
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which < subItems.size()) {
                            setGroup(subItems.get(which));
                        }
                    }
                })
                .create()
                .show();
    }

    private void popTopic() {
        if (topics.size() == 0) {
            return;
        }
        final String[] items = new String[topics.size()];
        for (int i = 0; i < topics.size(); i++) {
            items[i] = topics.get(i).key;
        }
        new AlertDialog.Builder(this)
                .setItems(items, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        setTopic(which);
                    }
                })
                .create()
                .show();
    }

    public void publishPost(String group_id, String csrf, String title, String body, String topic) {
        NetworkTask task = PostAPI.publishPost(group_id, csrf, title, body, topic,
                checkBox != null && checkBox.isChecked(), new SimpleCallBack<String>() {
                    @Override
                    public void onFailure() {
                        Mob.onEvent(Mob.Event_Publish_Post_Failed);
                        new AlertDialog.Builder(PublishPostActivity.this)
                                .setTitle(R.string.hint)
                                .setMessage(R.string.publish_post_failed)
                                .setPositiveButton(R.string.ok, null)
                                .show();
                        UiUtil.dismissDialog(progressDialog);
                    }

                    @Override
                    public void onSuccess() {
                        UiUtil.dismissDialog(progressDialog);
                        Mob.onEvent(Mob.Event_Publish_Post_OK);
                        toast(R.string.publish_post_ok);
                        setResult(RESULT_OK);
                        replyOK = true;
                        tryClearSketch();
                        finish();
                    }
                });
        showDialog(task);
    }

    private void showDialog(final NetworkTask task) {
        UiUtil.dismissDialog(progressDialog);
        progressDialog = new ProgressDialog(PublishPostActivity.this);
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setMessage(getString(R.string.message_wait_a_minute));
        progressDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            @Override
            public void onCancel(DialogInterface dialog) {
                task.dismiss();
            }
        });
        progressDialog.show();
    }

    private void dismissDialog() {
        if (progressDialog != null && progressDialog.isShowing()) {
            progressDialog.dismiss();
        }
    }

    private void resetImageButtons() {
        tmpImagePath = "";
        insertButton.setVisibility(View.GONE);
        imgButton.setVisibility(View.VISIBLE);
        uploadingProgress.setVisibility(View.GONE);
    }

    private void setImageButtonsUploading() {
        insertButton.setVisibility(View.GONE);
        imgButton.setVisibility(View.GONE);
        uploadingProgress.setVisibility(View.VISIBLE);
    }

    private void setImageButtonsPrepared() {
        insertButton.setVisibility(View.VISIBLE);
        imgButton.setVisibility(View.GONE);
        uploadingProgress.setVisibility(View.GONE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case RequestCode.Code_Invoke_Image_Selector:
                    Uri uri = data.getData();
                    String path = FileUtil.getActualPath(this, uri);
                    if (!TextUtils.isEmpty(path)) {
                        uploadImage(path);
                    }
                    break;
                case RequestCode.Code_Invoke_Camera:
                    if (tmpUploadFile != null) {
                        uploadImage(tmpUploadFile.getAbsolutePath());
                    }
                    break;
                default:
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_publish_post, menu);
        menu.findItem(R.id.action_anon).setVisible(true);
        int padding = DisplayUtil.dip2px(6, this);
        checkBox = (CheckBox) menu.findItem(R.id.action_anon).getActionView();
        checkBox.setPadding(padding, 0, padding * 2, 0);
        checkBox.setText(R.string.anon);
        checkBox.setTextColor(Color.parseColor("#ffffff"));
        checkBox.setBackgroundColor(0);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        switch (id) {
            case android.R.id.home:
                finish();
                break;
            case R.id.action_reload_csrf:
                prepare();
                break;
        }
        return super.onOptionsItemSelected(item);
    }
}
