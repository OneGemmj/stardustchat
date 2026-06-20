package com.example.stardustchat;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 聊天历史列表的适配器，用于RecyclerView展示历史聊天记录
 */

public class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder> {

    // 聊天历史数据列表
    private List<ChatHistory> historyList;
    // 历史项点击事件监听器
    private OnHistoryClickListener listener;

    /**
     * 定义点击历史项的回调接口
     */
    public interface OnHistoryClickListener {
        void onHistoryClick(ChatHistory history);// 当用户点击历史项时回调
    }

    /**
     * 构造方法
     * historyList 聊天历史数据列表
     * listener 点击监听器
     */
    public HistoryAdapter(List<ChatHistory> historyList, OnHistoryClickListener listener) {
        this.historyList = historyList;// 初始化历史数据
        this.listener = listener;// 初始化监听器
    }

    /**
     * 创建ViewHolder，加载每个聊天历史项的布局
     */
    @NonNull
    @Override
    public HistoryViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        // 加载item布局文件
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_chat_history, parent, false);
        return new HistoryViewHolder(view);// 返回ViewHolder实例
    }

    /**
     * 将数据绑定到ViewHolder控件上
     */
    @Override
    public void onBindViewHolder(@NonNull HistoryViewHolder holder, int position) {
        ChatHistory history = historyList.get(position);// 获取当前位置的数据
        holder.bind(history);// 绑定数据到控件
    }

    /**
     * 获取历史数据总数
     */
    // 返回列表数量
    @Override
    public int getItemCount() {
        return historyList != null ? historyList.size() : 0;
    }

    /**
     * 更新历史数据并刷新界面
     * newHistoryList 新的数据列表
     */
    public void updateHistoryList(List<ChatHistory> newHistoryList) {
        this.historyList = newHistoryList;// 更新数据
        notifyDataSetChanged();// 通知刷新
    }

    /**
     * 聊天历史项的ViewHolder类
     */
    class HistoryViewHolder extends RecyclerView.ViewHolder {
        // 历史项标题
        private TextView titleText;
        // 时间显示
        private TextView timeText;
        // 聊天预览内容
        private TextView previewText;

        /**
         * 构造方法，初始化控件
         */
        public HistoryViewHolder(@NonNull View itemView) {
            super(itemView);
            titleText = itemView.findViewById(R.id.history_title);// 标题控件
            timeText = itemView.findViewById(R.id.history_time);// 时间控件
            previewText = itemView.findViewById(R.id.history_preview);// 聊天预览控件

            // 设置点击事件，点击某一项回调接口
            itemView.setOnClickListener(v -> {
                if (listener != null && getAdapterPosition() != RecyclerView.NO_POSITION) {
                    listener.onHistoryClick(historyList.get(getAdapterPosition()));
                }
            });
        }

        /**
         * 绑定聊天历史数据到控件
         * history 当前历史数据
         */
        public void bind(ChatHistory history) {
            if (history != null) {
                // 设置标题，如果为空则显示“未知对话”
                titleText.setText(history.getTitle() != null
                        ? history.getTitle()
                        : itemView.getContext().getString(R.string.unknown_chat));
                // 设置时间
                timeText.setText(formatTime(history.getTimestamp()));
                // 设置聊天消息预览
                previewText.setText(history.getLastMessage() != null ? history.getLastMessage() : "");
            }
        }

        /**
         * 格式化时间戳为“MM-dd HH:mm”格式字符串
         *timestamp 时间戳
         * 格式化后的时间字符串
         */
        private String formatTime(long timestamp) {
            if (timestamp <= 0) return "";// 若无效时间戳则返回空
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.getDefault());// 定义时间格式
            return sdf.format(new Date(timestamp));// 返回格式化字符串
        }
    }
}
