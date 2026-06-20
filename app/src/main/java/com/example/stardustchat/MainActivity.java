package com.example.stardustchat;

import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Base64;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.checkbox.MaterialCheckBox;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.navigation.NavigationView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    private static final String CHAT_PREFS = "ChatHistory";
    private static final String SETTINGS_PREFS = "ApiSettings";

    private static final String KEY_API_BASE_URL = "api_base_url";
    private static final String KEY_API_KEY = "api_key";
    private static final String KEY_SELECTED_MODEL = "selected_model";
    private static final String KEY_MODEL_LIST = "model_list";
    private static final String KEY_VISION_ENABLED = "vision_enabled";
    private static final String KEY_TOOLS_ENABLED = "tools_enabled";
    private static final String KEY_TAVILY_API_KEY = "tavily_api_key";
    private static final String KEY_BRAVE_API_KEY = "brave_api_key";

    private static final String DEFAULT_API_BASE_URL = "https://api.openai.com/v1";
    private static final String DEFAULT_MODEL = "gpt-4o-mini";
    private static final String TAVILY_KEY_URL = "https://app.tavily.com/home";
    private static final String BRAVE_KEY_URL = "https://api-dashboard.search.brave.com/app/keys";
    private static final String TAVILY_SEARCH_URL = "https://api.tavily.com/search";
    private static final String BRAVE_SEARCH_URL = "https://api.search.brave.com/res/v1/web/search";
    private static final long MAX_ATTACHMENT_BYTES = 25L * 1024L * 1024L;

    private EditText messageInput;
    private Button sendButton;
    private Button attachButton;
    private TextView attachmentStatus;
    private RecyclerView recyclerView;
    private ChatAdapter adapter;
    private final List<ChatMessage> messageList = new ArrayList<>();
    private final List<AttachmentItem> pendingAttachments = new ArrayList<>();

    private DrawerLayout drawerLayout;
    private NavigationView navigationView;
    private Toolbar toolbar;

    private SharedPreferences chatPrefs;
    private SharedPreferences settingsPrefs;
    private int currentHistoryId;

    private OkHttpClient httpClient;
    private ActivityResultLauncher<Intent> attachmentPicker;

    @Override
    protected void attachBaseContext(Context newBase) {
        super.attachBaseContext(LocaleHelper.applyLocale(newBase));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        chatPrefs = getSharedPreferences(CHAT_PREFS, MODE_PRIVATE);
        settingsPrefs = getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE);
        httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(90, TimeUnit.SECONDS)
                .writeTimeout(90, TimeUnit.SECONDS)
                .build();

        currentHistoryId = chatPrefs.getInt("last_history_id", -1);
        if (currentHistoryId == -1) {
            currentHistoryId = (int) (System.currentTimeMillis() / 1000);
            chatPrefs.edit().putInt("last_history_id", currentHistoryId).apply();
        }

        initViews();
        setupToolbar();
        setupNavigationDrawer();
        setupRecyclerView();
        setupAttachmentPicker();
        setupSendButton();
        ensureHistoryExists();

        handleHistoryLoadRequest();
        handleWelcomeMessage();

        if (getIntent().getBooleanExtra("open_settings", false)) {
            recyclerView.post(this::showSettings);
        }
    }

    private void initViews() {
        messageInput = findViewById(R.id.messageInput);
        sendButton = findViewById(R.id.sendButton);
        attachButton = findViewById(R.id.attachButton);
        attachmentStatus = findViewById(R.id.attachmentStatus);
        recyclerView = findViewById(R.id.recyclerView);
        drawerLayout = findViewById(R.id.drawer_layout);
        navigationView = findViewById(R.id.nav_view);
        toolbar = findViewById(R.id.toolbar);
    }

    private void setupToolbar() {
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowTitleEnabled(true);
            getSupportActionBar().setTitle(R.string.app_name);
        }
    }

    private void setupNavigationDrawer() {
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.navigation_drawer_open, R.string.navigation_drawer_close);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        navigationView.setNavigationItemSelectedListener(this);
        navigationView.setCheckedItem(R.id.nav_current_chat);
    }

    private void setupRecyclerView() {
        adapter = new ChatAdapter(messageList);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setAdapter(adapter);
    }

    private void setupAttachmentPicker() {
        attachmentPicker = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        handleSelectedAttachments(result.getData());
                    }
                });

        attachButton.setOnClickListener(v -> {
            if (!settingsPrefs.getBoolean(KEY_VISION_ENABLED, false)) {
                Toast.makeText(this, R.string.toast_enable_upload, Toast.LENGTH_SHORT).show();
                return;
            }
            openAttachmentPicker();
        });
    }

    private void setupSendButton() {
        sendButton.setOnClickListener(v -> {
            String text = messageInput.getText().toString().trim();
            if (TextUtils.isEmpty(text) && pendingAttachments.isEmpty()) {
                return;
            }
            if (TextUtils.isEmpty(text)) {
                text = getString(R.string.prompt_analyze_attachments);
            }

            List<AttachmentItem> attachments = new ArrayList<>(pendingAttachments);
            pendingAttachments.clear();
            updateAttachmentStatus();
            messageInput.setText("");

            submitUserMessage(text, attachments);
        });
    }

    public void sendEditedUserMessage(String editedText) {
        if (!TextUtils.isEmpty(editedText)) {
            submitUserMessage(editedText.trim(), new ArrayList<>());
        }
    }

    private void handleWelcomeMessage() {
        String firstMsg = getIntent().getStringExtra("first_message");
        if (!TextUtils.isEmpty(firstMsg)) {
            submitUserMessage(firstMsg.trim(), new ArrayList<>());
        }
    }

    private void handleHistoryLoadRequest() {
        String loadHistory = getIntent().getStringExtra("load_history");
        if (!TextUtils.isEmpty(loadHistory)) {
            recyclerView.post(() -> loadHistoryByPeriod(loadHistory));
        }
    }

    private void submitUserMessage(String message, List<AttachmentItem> attachments) {
        String apiBaseUrl = settingsPrefs.getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL);
        if (TextUtils.isEmpty(apiBaseUrl)) {
            Toast.makeText(this, R.string.toast_api_base_required, Toast.LENGTH_SHORT).show();
            showSettings();
            return;
        }

        String model = getSelectedModel();
        if (TextUtils.isEmpty(model)) {
            Toast.makeText(this, R.string.toast_model_required, Toast.LENGTH_SHORT).show();
            showSettings();
            return;
        }

        if (!attachments.isEmpty() && !settingsPrefs.getBoolean(KEY_VISION_ENABLED, false)) {
            Toast.makeText(this, R.string.toast_upload_disabled, Toast.LENGTH_SHORT).show();
            return;
        }

        List<ChatMessage> historySnapshot = new ArrayList<>(messageList);
        String displayText = message + buildAttachmentSummary(attachments);
        addMessage(displayText, ChatMessage.TYPE_USER, true);
        int thinkingIndex = addMessage(getString(R.string.ai_thinking), ChatMessage.TYPE_BOT, false);

        sendUserMessage(message, attachments, historySnapshot, thinkingIndex);
    }

    private int addMessage(String content, int type, boolean save) {
        ChatMessage newMessage = new ChatMessage(content, type);
        messageList.add(newMessage);
        int pos = messageList.size() - 1;
        adapter.notifyItemInserted(pos);
        recyclerView.scrollToPosition(pos);
        if (save) {
            saveChatHistory(newMessage, currentHistoryId);
        }
        return pos;
    }

    private void updateBotMessage(int index, String content, boolean save) {
        runOnUiThread(() -> {
            if (index < 0 || index >= messageList.size()) {
                return;
            }
            ChatMessage message = messageList.get(index);
            message.setContent(content);
            message.setTimestamp(System.currentTimeMillis());
            adapter.notifyItemChanged(index);
            recyclerView.scrollToPosition(index);
            if (save) {
                saveChatHistory(message, currentHistoryId);
            }
        });
    }

    private void sendUserMessage(String message,
                                 List<AttachmentItem> attachments,
                                 List<ChatMessage> historySnapshot,
                                 int thinkingIndex) {
        new Thread(() -> {
            try {
                JSONArray messages = buildConversationMessages(historySnapshot, message, attachments);
                String responseText = performChatCompletion(messages);
                updateBotMessage(thinkingIndex, cleanAssistantText(responseText), true);
            } catch (Exception e) {
                updateBotMessage(thinkingIndex, getString(R.string.request_failed, e.getMessage()), true);
            }
        }).start();
    }

    private JSONArray buildConversationMessages(List<ChatMessage> historySnapshot,
                                                String userMessage,
                                                List<AttachmentItem> attachments) throws Exception {
        JSONArray messages = new JSONArray();
        int start = Math.max(0, historySnapshot.size() - 12);
        for (int i = start; i < historySnapshot.size(); i++) {
            ChatMessage chatMessage = historySnapshot.get(i);
            if (chatMessage == null || TextUtils.isEmpty(chatMessage.getContent())) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("role", chatMessage.getType() == ChatMessage.TYPE_USER ? "user" : "assistant");
            item.put("content", stripAttachmentSummary(chatMessage.getContent()));
            messages.put(item);
        }

        JSONObject currentUser = new JSONObject();
        currentUser.put("role", "user");
        currentUser.put("content", buildUserContent(userMessage, attachments));
        messages.put(currentUser);
        return messages;
    }

    private Object buildUserContent(String text, List<AttachmentItem> attachments) throws Exception {
        if (attachments.isEmpty()) {
            return text;
        }

        JSONArray parts = new JSONArray();
        JSONObject textPart = new JSONObject();
        textPart.put("type", "text");
        textPart.put("text", text);
        parts.put(textPart);

        for (AttachmentItem attachment : attachments) {
            String dataUrl = readAttachmentDataUrl(attachment);
            JSONObject part = new JSONObject();
            if (attachment.mimeType.startsWith("image/")) {
                JSONObject imageUrl = new JSONObject();
                imageUrl.put("url", dataUrl);
                part.put("type", "image_url");
                part.put("image_url", imageUrl);
            } else if (attachment.mimeType.startsWith("video/")) {
                JSONObject videoUrl = new JSONObject();
                videoUrl.put("url", dataUrl);
                part.put("type", "video_url");
                part.put("video_url", videoUrl);
            } else {
                continue;
            }
            parts.put(part);
        }
        return parts;
    }

    private String performChatCompletion(JSONArray messages) throws Exception {
        String firstResponse = runChatCompletion(messages, true);
        JSONObject assistantMessage = parseAssistantMessage(firstResponse);
        JSONArray toolCalls = assistantMessage.optJSONArray("tool_calls");

        if (toolCalls != null && toolCalls.length() > 0) {
            JSONObject assistantForHistory = new JSONObject();
            assistantForHistory.put("role", "assistant");
            assistantForHistory.put("content", assistantMessage.optString("content", ""));
            assistantForHistory.put("tool_calls", toolCalls);
            messages.put(assistantForHistory);

            JSONArray toolMessages = executeToolCalls(toolCalls);
            for (int i = 0; i < toolMessages.length(); i++) {
                messages.put(toolMessages.getJSONObject(i));
            }

            String finalResponse = runChatCompletion(messages, false);
            return parseAssistantMessage(finalResponse).optString("content", "");
        }

        return assistantMessage.optString("content", "");
    }

    private String runChatCompletion(JSONArray messages, boolean includeTools) throws Exception {
        String apiKey = settingsPrefs.getString(KEY_API_KEY, "").trim();
        JSONObject body = new JSONObject();
        body.put("model", getSelectedModel());
        body.put("messages", messages);
        body.put("max_tokens", 6400);
        body.put("stream", false);

        JSONArray tools = buildSearchTools();
        if (includeTools && settingsPrefs.getBoolean(KEY_TOOLS_ENABLED, false) && tools.length() > 0) {
            body.put("tools", tools);
            body.put("tool_choice", "auto");
        }

        Request.Builder builder = new Request.Builder()
                .url(getChatCompletionsUrl())
                .post(RequestBody.create(JSON, body.toString()))
                .addHeader("Content-Type", "application/json");
        if (!TextUtils.isEmpty(apiKey)) {
            builder.addHeader("Authorization", "Bearer " + apiKey);
        }

        try (Response response = httpClient.newCall(builder.build()).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                throw new IOException(getString(R.string.ai_api_http_error, response.code(), truncate(responseBody)));
            }
            return responseBody;
        }
    }

    private JSONObject parseAssistantMessage(String responseBody) throws Exception {
        JSONObject root = new JSONObject(responseBody);
        JSONObject error = root.optJSONObject("error");
        if (error != null) {
            throw new IOException(error.optString("message", error.toString()));
        }

        JSONArray choices = root.optJSONArray("choices");
        if (choices == null || choices.length() == 0) {
            throw new IOException(getString(R.string.ai_no_choices, truncate(responseBody)));
        }
        return choices.getJSONObject(0).getJSONObject("message");
    }

    private JSONArray buildSearchTools() throws Exception {
        JSONArray tools = new JSONArray();
        if (!TextUtils.isEmpty(settingsPrefs.getString(KEY_TAVILY_API_KEY, "").trim())) {
            tools.put(createSearchTool("tavily_search", "Use Tavily to search the public web for fresh information."));
        }
        if (!TextUtils.isEmpty(settingsPrefs.getString(KEY_BRAVE_API_KEY, "").trim())) {
            tools.put(createSearchTool("brave_search", "Use Brave Search API to search the public web for fresh information."));
        }
        return tools;
    }

    private JSONObject createSearchTool(String name, String description) throws Exception {
        JSONObject queryProperty = new JSONObject();
        queryProperty.put("type", "string");
        queryProperty.put("description", "Search query");

        JSONObject properties = new JSONObject();
        properties.put("query", queryProperty);

        JSONArray required = new JSONArray();
        required.put("query");

        JSONObject parameters = new JSONObject();
        parameters.put("type", "object");
        parameters.put("properties", properties);
        parameters.put("required", required);

        JSONObject function = new JSONObject();
        function.put("name", name);
        function.put("description", description);
        function.put("parameters", parameters);

        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        tool.put("function", function);
        return tool;
    }

    private JSONArray executeToolCalls(JSONArray toolCalls) throws Exception {
        JSONArray toolMessages = new JSONArray();
        for (int i = 0; i < toolCalls.length(); i++) {
            JSONObject toolCall = toolCalls.getJSONObject(i);
            JSONObject function = toolCall.optJSONObject("function");
            String name = function != null ? function.optString("name") : "";
            String argsText = function != null ? function.optString("arguments", "{}") : "{}";
            String query = extractQuery(argsText);

            String content;
            if (TextUtils.isEmpty(query)) {
                content = getString(R.string.tool_missing_query);
            } else if ("tavily_search".equals(name)) {
                content = callTavilySearch(query);
            } else if ("brave_search".equals(name)) {
                content = callBraveSearch(query);
            } else {
                content = getString(R.string.unknown_tool, name);
            }

            JSONObject toolMessage = new JSONObject();
            toolMessage.put("role", "tool");
            toolMessage.put("tool_call_id", toolCall.optString("id"));
            toolMessage.put("content", content);
            toolMessages.put(toolMessage);
        }
        return toolMessages;
    }

    private String extractQuery(String argsText) {
        try {
            JSONObject args = new JSONObject(argsText);
            return args.optString("query", "").trim();
        } catch (Exception ignored) {
            return argsText == null ? "" : argsText.trim();
        }
    }

    private String callTavilySearch(String query) throws Exception {
        String apiKey = settingsPrefs.getString(KEY_TAVILY_API_KEY, "").trim();
        if (TextUtils.isEmpty(apiKey)) {
            return getString(R.string.tavily_key_missing);
        }

        JSONObject body = new JSONObject();
        body.put("query", query);
        body.put("search_depth", "basic");
        body.put("include_answer", true);
        body.put("include_raw_content", false);
        body.put("max_results", 5);

        Request request = new Request.Builder()
                .url(TAVILY_SEARCH_URL)
                .post(RequestBody.create(JSON, body.toString()))
                .addHeader("Content-Type", "application/json")
                .addHeader("Authorization", "Bearer " + apiKey)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                return getString(R.string.tavily_search_failed, response.code(), truncate(responseBody));
            }
            return formatTavilyResponse(responseBody);
        }
    }

    private String callBraveSearch(String query) throws Exception {
        String apiKey = settingsPrefs.getString(KEY_BRAVE_API_KEY, "").trim();
        if (TextUtils.isEmpty(apiKey)) {
            return getString(R.string.brave_key_missing);
        }

        HttpUrl url = HttpUrl.parse(BRAVE_SEARCH_URL).newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("count", "5")
                .addQueryParameter("safesearch", "moderate")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("X-Subscription-Token", apiKey)
                .build();

        try (Response response = httpClient.newCall(request).execute()) {
            String responseBody = response.body() != null ? response.body().string() : "";
            if (!response.isSuccessful()) {
                return getString(R.string.brave_search_failed, response.code(), truncate(responseBody));
            }
            return formatBraveResponse(responseBody);
        }
    }

    private String formatTavilyResponse(String responseBody) {
        try {
            JSONObject root = new JSONObject(responseBody);
            StringBuilder builder = new StringBuilder();
            String answer = root.optString("answer", "");
            if (!TextUtils.isEmpty(answer)) {
                builder.append("Answer: ").append(answer).append("\n\n");
            }
            JSONArray results = root.optJSONArray("results");
            if (results != null) {
                for (int i = 0; i < Math.min(results.length(), 5); i++) {
                    JSONObject result = results.getJSONObject(i);
                    builder.append(i + 1).append(". ")
                            .append(result.optString("title", "Untitled")).append("\n")
                            .append(result.optString("url", "")).append("\n")
                            .append(result.optString("content", "")).append("\n\n");
                }
            }
            return TextUtils.isEmpty(builder.toString().trim()) ? truncate(responseBody) : builder.toString().trim();
        } catch (Exception ignored) {
            return truncate(responseBody);
        }
    }

    private String formatBraveResponse(String responseBody) {
        try {
            JSONObject root = new JSONObject(responseBody);
            JSONObject web = root.optJSONObject("web");
            JSONArray results = web != null ? web.optJSONArray("results") : null;
            StringBuilder builder = new StringBuilder();
            if (results != null) {
                for (int i = 0; i < Math.min(results.length(), 5); i++) {
                    JSONObject result = results.getJSONObject(i);
                    builder.append(i + 1).append(". ")
                            .append(result.optString("title", "Untitled")).append("\n")
                            .append(result.optString("url", "")).append("\n")
                            .append(result.optString("description", "")).append("\n\n");
                }
            }
            return TextUtils.isEmpty(builder.toString().trim()) ? truncate(responseBody) : builder.toString().trim();
        } catch (Exception ignored) {
            return truncate(responseBody);
        }
    }

    private void openAttachmentPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"image/*", "video/*"});
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        attachmentPicker.launch(intent);
    }

    private void handleSelectedAttachments(Intent data) {
        int added = 0;
        ClipData clipData = data.getClipData();
        if (clipData != null) {
            for (int i = 0; i < clipData.getItemCount(); i++) {
                if (addAttachment(clipData.getItemAt(i).getUri())) {
                    added++;
                }
            }
        } else if (data.getData() != null && addAttachment(data.getData())) {
            added++;
        }
        updateAttachmentStatus();
        if (added > 0) {
            Toast.makeText(this, getString(R.string.toast_added_attachments, added), Toast.LENGTH_SHORT).show();
        }
    }

    private boolean addAttachment(Uri uri) {
        String mimeType = getContentResolver().getType(uri);
        if (TextUtils.isEmpty(mimeType)
                || !(mimeType.startsWith("image/") || mimeType.startsWith("video/"))) {
            Toast.makeText(this, R.string.toast_unsupported_file, Toast.LENGTH_SHORT).show();
            return false;
        }

        try {
            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (Exception ignored) {
        }

        long size = querySize(uri);
        pendingAttachments.add(new AttachmentItem(uri, mimeType, queryDisplayName(uri), size));
        return true;
    }

    private void updateAttachmentStatus() {
        if (pendingAttachments.isEmpty()) {
            attachmentStatus.setVisibility(View.GONE);
            attachmentStatus.setText("");
            return;
        }
        attachmentStatus.setVisibility(View.VISIBLE);
        attachmentStatus.setText(getString(
                R.string.attachment_count_status,
                pendingAttachments.size(),
                joinAttachmentNames(pendingAttachments)));
    }

    private String readAttachmentDataUrl(AttachmentItem attachment) throws Exception {
        if (attachment.sizeBytes > MAX_ATTACHMENT_BYTES) {
            throw new IOException(getString(R.string.attachment_too_large, attachment.displayName));
        }
        byte[] bytes = readBytes(attachment.uri);
        String encoded = Base64.encodeToString(bytes, Base64.NO_WRAP);
        return "data:" + attachment.mimeType + ";base64," + encoded;
    }

    private byte[] readBytes(Uri uri) throws Exception {
        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (inputStream == null) {
                throw new IOException(getString(R.string.attachment_read_failed));
            }
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, read);
                if (outputStream.size() > MAX_ATTACHMENT_BYTES) {
                    throw new IOException(getString(R.string.attachment_too_large, getString(R.string.attachment_generic)));
                }
            }
            return outputStream.toByteArray();
        }
    }

    private String queryDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (index >= 0) {
                    String name = cursor.getString(index);
                    if (!TextUtils.isEmpty(name)) {
                        return name;
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return getString(R.string.attachment_default_name);
    }

    private long querySize(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (index >= 0) {
                    return cursor.getLong(index);
                }
            }
        } catch (Exception ignored) {
        }
        return -1L;
    }

    private String buildAttachmentSummary(List<AttachmentItem> attachments) {
        if (attachments.isEmpty()) {
            return "";
        }
        return getString(R.string.attachment_summary, attachments.size(), joinAttachmentNames(attachments));
    }

    private String joinAttachmentNames(List<AttachmentItem> attachments) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < attachments.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(attachments.get(i).displayName);
        }
        return builder.toString();
    }

    private String stripAttachmentSummary(String content) {
        int marker = content.indexOf("\n\n[");
        return marker >= 0 ? content.substring(0, marker) : content;
    }

    private String getChatCompletionsUrl() {
        String raw = settingsPrefs.getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL).trim();
        if (raw.endsWith("/chat/completions")) {
            return raw;
        }
        return normalizeApiBaseUrl(raw) + "/chat/completions";
    }

    private String getModelsUrl(String rawApiBaseUrl) {
        return normalizeApiBaseUrl(rawApiBaseUrl) + "/models";
    }

    private String normalizeApiBaseUrl(String rawUrl) {
        String url = TextUtils.isEmpty(rawUrl) ? DEFAULT_API_BASE_URL : rawUrl.trim();
        while (url.endsWith("/")) {
            url = url.substring(0, url.length() - 1);
        }
        if (url.endsWith("/chat/completions")) {
            url = url.substring(0, url.length() - "/chat/completions".length());
        }
        return url;
    }

    private String getSelectedModel() {
        String model = settingsPrefs.getString(KEY_SELECTED_MODEL, DEFAULT_MODEL);
        return model == null ? "" : model.trim();
    }

    private List<String> getSavedModels() {
        Set<String> models = new LinkedHashSet<>();
        models.add(DEFAULT_MODEL);
        String raw = settingsPrefs.getString(KEY_MODEL_LIST, "");
        if (!TextUtils.isEmpty(raw)) {
            try {
                JSONArray array = new JSONArray(raw);
                for (int i = 0; i < array.length(); i++) {
                    String model = array.optString(i, "").trim();
                    if (!TextUtils.isEmpty(model)) {
                        models.add(model);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        String selected = settingsPrefs.getString(KEY_SELECTED_MODEL, "").trim();
        if (!TextUtils.isEmpty(selected)) {
            models.add(selected);
        }
        return new ArrayList<>(models);
    }

    private void saveModels(List<String> models) {
        Set<String> uniqueModels = new LinkedHashSet<>();
        for (String model : models) {
            if (!TextUtils.isEmpty(model)) {
                uniqueModels.add(model.trim());
            }
        }
        JSONArray array = new JSONArray();
        for (String model : uniqueModels) {
            array.put(model);
        }
        settingsPrefs.edit().putString(KEY_MODEL_LIST, array.toString()).apply();
    }

    private void showSettings() {
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(20);
        content.setPadding(padding, padding, padding, padding);

        ArrayAdapter<CharSequence> languageAdapter = ArrayAdapter.createFromResource(
                this,
                R.array.language_labels,
                android.R.layout.simple_spinner_item);
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner languageSpinner = new Spinner(this);
        languageSpinner.setAdapter(languageAdapter);
        String currentLanguage = LocaleHelper.getLanguage(this);
        languageSpinner.setSelection(LocaleHelper.LANGUAGE_ZH.equals(currentLanguage) ? 1 : 0);

        EditText apiBaseInput = makeInput(getString(R.string.api_base_url_hint),
                settingsPrefs.getString(KEY_API_BASE_URL, DEFAULT_API_BASE_URL), false);
        EditText apiKeyInput = makeInput(getString(R.string.api_key_hint), settingsPrefs.getString(KEY_API_KEY, ""), true);

        List<String> dialogModels = getSavedModels();
        ArrayAdapter<String> modelAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, dialogModels);
        modelAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        Spinner modelSpinner = new Spinner(this);
        modelSpinner.setAdapter(modelAdapter);
        int selectedIndex = dialogModels.indexOf(getSelectedModel());
        if (selectedIndex >= 0) {
            modelSpinner.setSelection(selectedIndex);
        }

        EditText manualModelInput = makeInput(getString(R.string.manual_model_hint), "", false);
        Button addModelButton = new MaterialButton(this);
        addModelButton.setText(R.string.add_model);
        Button fetchModelsButton = new MaterialButton(this);
        fetchModelsButton.setText(R.string.fetch_models);

        addModelButton.setOnClickListener(v -> {
            String model = manualModelInput.getText().toString().trim();
            if (TextUtils.isEmpty(model)) {
                return;
            }
            if (!dialogModels.contains(model)) {
                dialogModels.add(model);
                modelAdapter.notifyDataSetChanged();
            }
            modelSpinner.setSelection(dialogModels.indexOf(model));
            manualModelInput.setText("");
        });

        fetchModelsButton.setOnClickListener(v -> fetchModels(
                apiBaseInput.getText().toString().trim(),
                apiKeyInput.getText().toString().trim(),
                dialogModels,
                modelAdapter,
                modelSpinner));

        MaterialCheckBox visionCheck = new MaterialCheckBox(this);
        visionCheck.setText(R.string.enable_vision_upload);
        visionCheck.setChecked(settingsPrefs.getBoolean(KEY_VISION_ENABLED, false));

        MaterialCheckBox toolsCheck = new MaterialCheckBox(this);
        toolsCheck.setText(R.string.enable_tools);
        toolsCheck.setChecked(settingsPrefs.getBoolean(KEY_TOOLS_ENABLED, false));

        EditText tavilyKeyInput = makeInput(getString(R.string.tavily_key_hint), settingsPrefs.getString(KEY_TAVILY_API_KEY, ""), true);
        Button tavilyLinkButton = new MaterialButton(this);
        tavilyLinkButton.setText(R.string.tavily_get_key);
        tavilyLinkButton.setOnClickListener(v -> openUrl(TAVILY_KEY_URL));

        EditText braveKeyInput = makeInput(getString(R.string.brave_key_hint), settingsPrefs.getString(KEY_BRAVE_API_KEY, ""), true);
        Button braveLinkButton = new MaterialButton(this);
        braveLinkButton.setText(R.string.brave_get_key);
        braveLinkButton.setOnClickListener(v -> openUrl(BRAVE_KEY_URL));

        addLabeledView(content, getString(R.string.settings_language_label), languageSpinner);
        addLabeledView(content, getString(R.string.api_base_url_label), apiBaseInput);
        addLabeledView(content, getString(R.string.api_key_label), apiKeyInput);
        addLabeledView(content, getString(R.string.model_label), modelSpinner);
        content.addView(manualModelInput);
        content.addView(addModelButton);
        content.addView(fetchModelsButton);
        content.addView(visionCheck);
        content.addView(toolsCheck);
        addLabeledView(content, getString(R.string.tavily_label), tavilyKeyInput);
        content.addView(tavilyLinkButton);
        addLabeledView(content, getString(R.string.brave_label), braveKeyInput);
        content.addView(braveLinkButton);

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(content);

        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.settings_title)
                .setView(scrollView)
                .setPositiveButton(R.string.save, (dialog, which) -> {
                    Object selectedItem = modelSpinner.getSelectedItem();
                    String selectedModel = selectedItem != null ? selectedItem.toString() : DEFAULT_MODEL;
                    String selectedLanguage = languageSpinner.getSelectedItemPosition() == 1
                            ? LocaleHelper.LANGUAGE_ZH
                            : LocaleHelper.LANGUAGE_EN;
                    boolean languageChanged = !selectedLanguage.equals(LocaleHelper.getLanguage(this));
                    saveModels(dialogModels);
                    settingsPrefs.edit()
                            .putString(LocaleHelper.KEY_LANGUAGE, selectedLanguage)
                            .putString(KEY_API_BASE_URL, apiBaseInput.getText().toString().trim())
                            .putString(KEY_API_KEY, apiKeyInput.getText().toString().trim())
                            .putString(KEY_SELECTED_MODEL, selectedModel)
                            .putBoolean(KEY_VISION_ENABLED, visionCheck.isChecked())
                            .putBoolean(KEY_TOOLS_ENABLED, toolsCheck.isChecked())
                            .putString(KEY_TAVILY_API_KEY, tavilyKeyInput.getText().toString().trim())
                            .putString(KEY_BRAVE_API_KEY, braveKeyInput.getText().toString().trim())
                            .apply();
                    Toast.makeText(this, R.string.toast_settings_saved, Toast.LENGTH_SHORT).show();
                    if (languageChanged) {
                        recreate();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    private EditText makeInput(String hint, String value, boolean password) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setSingleLine(true);
        input.setInputType(password
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD
                : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        return input;
    }

    private void addLabeledView(LinearLayout parent, String label, View view) {
        TextView textView = new TextView(this);
        textView.setText(label);
        textView.setTextSize(14);
        textView.setPadding(0, dp(12), 0, 0);
        parent.addView(textView);
        parent.addView(view);
    }

    private void fetchModels(String apiBaseUrl,
                             String apiKey,
                             List<String> dialogModels,
                             ArrayAdapter<String> modelAdapter,
                             Spinner modelSpinner) {
        if (TextUtils.isEmpty(apiBaseUrl)) {
            Toast.makeText(this, R.string.toast_api_base_enter_first, Toast.LENGTH_SHORT).show();
            return;
        }

        new Thread(() -> {
            try {
                Request.Builder builder = new Request.Builder()
                        .url(getModelsUrl(apiBaseUrl))
                        .get()
                        .addHeader("Accept", "application/json");
                if (!TextUtils.isEmpty(apiKey)) {
                    builder.addHeader("Authorization", "Bearer " + apiKey);
                }

                try (Response response = httpClient.newCall(builder.build()).execute()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    if (!response.isSuccessful()) {
                        throw new IOException("HTTP " + response.code() + ": " + truncate(responseBody));
                    }

                    List<String> fetchedModels = parseModelNames(responseBody);
                    runOnUiThread(() -> {
                        if (fetchedModels.isEmpty()) {
                            Toast.makeText(this, R.string.toast_no_models_found, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        for (String model : fetchedModels) {
                            if (!dialogModels.contains(model)) {
                                dialogModels.add(model);
                            }
                        }
                        modelAdapter.notifyDataSetChanged();
                        modelSpinner.setSelection(dialogModels.indexOf(fetchedModels.get(0)));
                        Toast.makeText(this, getString(R.string.toast_fetched_models, fetchedModels.size()), Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(
                        this,
                        getString(R.string.toast_fetch_models_failed, e.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    private List<String> parseModelNames(String responseBody) throws Exception {
        Set<String> models = new LinkedHashSet<>();
        JSONObject root = new JSONObject(responseBody);
        JSONArray data = root.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                Object item = data.get(i);
                if (item instanceof JSONObject) {
                    String id = ((JSONObject) item).optString("id", "").trim();
                    if (!TextUtils.isEmpty(id)) {
                        models.add(id);
                    }
                } else if (item instanceof String) {
                    models.add(((String) item).trim());
                }
            }
        }

        JSONArray fallback = root.optJSONArray("models");
        if (fallback != null) {
            for (int i = 0; i < fallback.length(); i++) {
                String model = fallback.optString(i, "").trim();
                if (!TextUtils.isEmpty(model)) {
                    models.add(model);
                }
            }
        }
        return new ArrayList<>(models);
    }

    private void openUrl(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, getString(R.string.toast_unable_to_open_link, url), Toast.LENGTH_SHORT).show();
        }
    }

    private void loadHistoryByPeriod(String period) {
        long startTime = 0;
        long endTime = System.currentTimeMillis();
        Calendar calendar = Calendar.getInstance();

        switch (period) {
            case "today":
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                startTime = calendar.getTimeInMillis();
                break;
            case "yesterday":
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                endTime = calendar.getTimeInMillis();
                calendar.add(Calendar.DAY_OF_MONTH, -1);
                startTime = calendar.getTimeInMillis();
                break;
            case "week":
                calendar.set(Calendar.DAY_OF_WEEK, calendar.getFirstDayOfWeek());
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                startTime = calendar.getTimeInMillis();
                break;
            case "older":
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                calendar.add(Calendar.DAY_OF_MONTH, -7);
                endTime = calendar.getTimeInMillis();
                startTime = 0;
                break;
            default:
                break;
        }

        List<ChatMessage> messages = AppDatabase.getInstance(this)
                .chatMessageDao()
                .getMessagesByTime(String.valueOf(currentHistoryId), startTime, endTime);
        messageList.clear();
        messageList.addAll(messages);
        adapter.notifyDataSetChanged();
    }

    private void ensureHistoryExists() {
        AppDatabase db = AppDatabase.getInstance(this);
        ChatHistory history = db.chatHistoryDao().getHistoryById(currentHistoryId);
        if (history == null) {
            ChatHistory newHistory = new ChatHistory();
            newHistory.id = currentHistoryId;
            newHistory.title = getString(R.string.new_chat);
            newHistory.lastMessage = "";
            newHistory.timestamp = System.currentTimeMillis();
            newHistory.messageCount = 0;
            db.chatHistoryDao().insert(newHistory);
        }
    }

    private void saveChatHistory(ChatMessage message, int historyId) {
        AppDatabase db = AppDatabase.getInstance(this);
        message.historyId = String.valueOf(historyId);
        message.timestamp = System.currentTimeMillis();
        db.chatMessageDao().insert(message);

        ChatHistory history = db.chatHistoryDao().getHistoryById(historyId);
        if (history == null) {
            return;
        }

        if (message.type == ChatMessage.TYPE_USER) {
            history.title = message.content.length() > 20
                    ? message.content.substring(0, 20) + "..."
                    : message.content;
        }
        history.lastMessage = message.content;
        history.timestamp = message.timestamp;
        history.messageCount = db.chatMessageDao().getMessageCount(String.valueOf(historyId));
        db.chatHistoryDao().update(history);
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.nav_current_chat) {
            Toast.makeText(this, R.string.toast_current_chat, Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_today) {
            loadHistoryByPeriod("today");
        } else if (id == R.id.nav_yesterday) {
            loadHistoryByPeriod("yesterday");
        } else if (id == R.id.nav_this_week) {
            loadHistoryByPeriod("week");
        } else if (id == R.id.nav_older) {
            loadHistoryByPeriod("older");
        } else if (id == R.id.nav_settings) {
            showSettings();
        } else if (id == R.id.nav_theme) {
            Toast.makeText(this, R.string.toast_theme_not_ready, Toast.LENGTH_SHORT).show();
        } else if (id == R.id.nav_about) {
            showAbout();
        }

        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }

    private void showAbout() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.about_title)
                .setMessage(R.string.about_message)
                .setPositiveButton(R.string.ok, null)
                .show();
    }

    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }

    private String cleanAssistantText(String text) {
        if (TextUtils.isEmpty(text) || "null".equals(text)) {
            return getString(R.string.ai_no_content);
        }
        return text.trim();
    }

    private String truncate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 800 ? text.substring(0, 800) + "..." : text;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private static class AttachmentItem {
        final Uri uri;
        final String mimeType;
        final String displayName;
        final long sizeBytes;

        AttachmentItem(Uri uri, String mimeType, String displayName, long sizeBytes) {
            this.uri = uri;
            this.mimeType = mimeType;
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
        }
    }
}
