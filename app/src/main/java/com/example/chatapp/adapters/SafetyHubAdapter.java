package com.example.chatapp.adapters;

import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatapp.R;
import com.example.chatapp.databinding.ItemContainerReceivedMessageBinding;
import com.example.chatapp.databinding.ItemContainerSentMessageBinding;
import com.example.chatapp.models.SafetyHubMessage;
import com.example.chatapp.utilities.MarkdownUtils;

import java.util.List;

public class SafetyHubAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private final List<SafetyHubMessage> safetyHubMessages;
    public static final int VIEW_TYPE_SENT = 1;
    public static final int VIEW_TYPE_RECEIVED = 2;

    public SafetyHubAdapter(List<SafetyHubMessage> safetyHubMessages) {
        this.safetyHubMessages = safetyHubMessages;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            return new SentMessageViewHolder(
                    ItemContainerSentMessageBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    )
            );
        } else {
            return new ReceivedMessageViewHolder(
                    ItemContainerReceivedMessageBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    )
            );
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (getItemViewType(position) == VIEW_TYPE_SENT) {
            ((SentMessageViewHolder) holder).setData(safetyHubMessages.get(position));
        } else {
            ((ReceivedMessageViewHolder) holder).setData(safetyHubMessages.get(position));
        }
    }

    @Override
    public int getItemCount() {
        return safetyHubMessages.size();
    }

    @Override
    public int getItemViewType(int position) {
        if (safetyHubMessages.get(position).isUser) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVED;
        }
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemContainerSentMessageBinding binding;

        SentMessageViewHolder(ItemContainerSentMessageBinding itemContainerSentMessageBinding) {
            super(itemContainerSentMessageBinding.getRoot());
            binding = itemContainerSentMessageBinding;
        }

        void setData(SafetyHubMessage safetyHubMessage) {
            binding.textMessage.setText(MarkdownUtils.formatMarkdown(binding.getRoot().getContext(), safetyHubMessage.message));
            binding.textDateTime.setText(safetyHubMessage.dateTime);
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        private final ItemContainerReceivedMessageBinding binding;

        ReceivedMessageViewHolder(ItemContainerReceivedMessageBinding itemContainerReceivedMessageBinding) {
            super(itemContainerReceivedMessageBinding.getRoot());
            binding = itemContainerReceivedMessageBinding;
        }

        void setData(SafetyHubMessage safetyHubMessage) {
            binding.textMessage.setText(MarkdownUtils.formatMarkdown(binding.getRoot().getContext(), safetyHubMessage.message));
            binding.textDateTime.setText(safetyHubMessage.dateTime);
            binding.imageProfile.setImageResource(R.drawable.ic_ai);
            binding.imageProfile.setBackgroundResource(R.drawable.background_image_white);
        }
    }
}
