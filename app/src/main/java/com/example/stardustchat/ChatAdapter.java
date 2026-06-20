package com.example.stardustchat;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<ChatMessage> messages;

    public ChatAdapter(List<ChatMessage> messages) {
        this.messages = messages;
    }

    @Override
    public int getItemViewType(int position) {
        return messages.get(position).getType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        if (viewType == ChatMessage.TYPE_USER) {
            View view = inflater.inflate(R.layout.item_chat_user, parent, false);
            return new UserViewHolder(view);
        }
        View view = inflater.inflate(R.layout.item_chat_bot, parent, false);
        return new BotViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        if (holder instanceof UserViewHolder) {
            bindUserMessage((UserViewHolder) holder, message);
        } else if (holder instanceof BotViewHolder) {
            ((BotViewHolder) holder).tvMessage.setText(message.getContent());
        }
    }

    private void bindUserMessage(UserViewHolder holder, ChatMessage message) {
        holder.tvMessage.setText(message.getContent());
        holder.itemView.findViewById(R.id.btnEdit).setOnClickListener(view -> {
            Context context = view.getContext();
            EditText input = new EditText(context);
            input.setText(message.getContent());
            input.setSelection(input.getText().length());

            new MaterialAlertDialogBuilder(context)
                    .setTitle(R.string.edit_message_title)
                    .setView(input)
                    .setPositiveButton(R.string.send, (dialog, which) -> {
                        String editedText = input.getText().toString().trim();
                        if (!editedText.isEmpty() && context instanceof MainActivity) {
                            ((MainActivity) context).sendEditedUserMessage(editedText);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
        });

        holder.itemView.findViewById(R.id.btnCopy).setOnClickListener(view -> {
            ClipboardManager clipboard = (ClipboardManager) view.getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (clipboard != null) {
                clipboard.setPrimaryClip(ClipData.newPlainText("message", message.getContent()));
                Toast.makeText(view.getContext(), R.string.toast_copied, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override
    public int getItemCount() {
        return messages.size();
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage;

        UserViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
        }
    }

    static class BotViewHolder extends RecyclerView.ViewHolder {
        final TextView tvMessage;

        BotViewHolder(View itemView) {
            super(itemView);
            tvMessage = itemView.findViewById(R.id.tvMessage);
        }
    }
}
