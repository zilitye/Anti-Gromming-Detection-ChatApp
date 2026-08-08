package com.example.chatapp.adapters;

import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatapp.R;
import com.example.chatapp.databinding.ItemChatProfileHeaderBinding;
import com.example.chatapp.databinding.ItemContainerReceivedMessageBinding;
import com.example.chatapp.databinding.ItemContainerSentMessageBinding;
import com.example.chatapp.models.ChatMessage;
import com.example.chatapp.models.User;
import com.example.chatapp.utilities.MarkdownUtils;

import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>{
    private final List<ChatMessage> chatMessages;
    private Bitmap receiverProfileImage;
    private final String senderId;
    private final User receiverUser;
    private final ProfileHeaderListener profileHeaderListener;

    public static final int VIEW_TYPE_HEADER = 0;
    public static final int VIEW_TYPE_SENT = 1;
    public static final int VIEW_TYPE_RECEIVE = 2;

    public interface ProfileHeaderListener {
        void onViewProfileClicked();
    }

    public void setReceiverProfileImage(Bitmap bitmap){
        receiverProfileImage = bitmap;
        notifyItemChanged(0);
    }

    public ChatAdapter(List<ChatMessage> chatMessages, Bitmap receiverProfileImage, String senderId, User receiverUser, ProfileHeaderListener listener) {
        this.chatMessages = chatMessages;
        this.receiverProfileImage = receiverProfileImage;
        this.senderId = senderId;
        this.receiverUser = receiverUser;
        this.profileHeaderListener = listener;
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if(viewType == VIEW_TYPE_HEADER){
            return new HeaderViewHolder(
                    ItemChatProfileHeaderBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    )
            );
        } else if(viewType == VIEW_TYPE_SENT){
            return new SentMessageViewHolder(
                    ItemContainerSentMessageBinding.inflate(
                            LayoutInflater.from(parent.getContext()),
                            parent,
                            false
                    )
            );
        }else{
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
        if(getItemViewType(position) == VIEW_TYPE_HEADER){
            ((HeaderViewHolder) holder).setData(receiverUser, receiverProfileImage, profileHeaderListener);
        } else if(getItemViewType(position) == VIEW_TYPE_SENT){
            int index = chatMessages.isEmpty() ? position - 1 : position;
            ((SentMessageViewHolder) holder).setData(chatMessages.get(index));
        }else{
            int index = chatMessages.isEmpty() ? position - 1 : position;
            ((ReceivedMessageViewHolder) holder).setData(chatMessages.get(index), receiverProfileImage);
        }
    }

    @Override
    public int getItemCount() {
        if (chatMessages.isEmpty()) {
            return 1; // Show header only
        } else {
            return chatMessages.size(); // Show only messages
        }
    }

    @Override
    public int getItemViewType(int position) {
        if (chatMessages.isEmpty()) {
            return VIEW_TYPE_HEADER;
        }
        
        if (chatMessages.get(position).senderId.equals(senderId)) {
            return VIEW_TYPE_SENT;
        } else {
            return VIEW_TYPE_RECEIVE;
        }
    }

    static class HeaderViewHolder extends RecyclerView.ViewHolder {
        private final ItemChatProfileHeaderBinding binding;

        HeaderViewHolder(ItemChatProfileHeaderBinding itemChatProfileHeaderBinding) {
            super(itemChatProfileHeaderBinding.getRoot());
            binding = itemChatProfileHeaderBinding;
        }

        void setData(User user, Bitmap profileImage, ProfileHeaderListener listener) {
            if(profileImage != null) {
                binding.imageProfile.setImageBitmap(profileImage);
            }
            binding.textName.setText(user.name);
            binding.imageVerified.setVisibility(android.view.View.GONE);
            binding.textUsername.setText(binding.getRoot().getContext().getString(R.string.username_format, user.email));
            binding.textStats.setVisibility(android.view.View.GONE);
            binding.buttonViewProfile.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(binding.getRoot().getContext(), R.color.macos_accent)
            ));
            binding.buttonViewProfile.setOnClickListener(v -> {
                if(listener != null) listener.onViewProfileClicked();
            });
        }
    }

    static class SentMessageViewHolder extends RecyclerView.ViewHolder{
        private final ItemContainerSentMessageBinding binding;

        SentMessageViewHolder(ItemContainerSentMessageBinding itemContainerSentMessageBinding){
            super(itemContainerSentMessageBinding.getRoot());
            binding = itemContainerSentMessageBinding;
        }
        void setData(ChatMessage chatMessage){
            binding.textMessage.setText(MarkdownUtils.formatMarkdown(binding.getRoot().getContext(), chatMessage.message));
            binding.textDateTime.setText(chatMessage.dateTime);
            if (chatMessage.isFlagged) {
                binding.imageWarning.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.imageWarning.setVisibility(android.view.View.GONE);
            }
        }
    }

    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder{
        private final ItemContainerReceivedMessageBinding binding;
        ReceivedMessageViewHolder(ItemContainerReceivedMessageBinding itemContainerReceivedMessageBinding){
            super(itemContainerReceivedMessageBinding.getRoot());
            binding = itemContainerReceivedMessageBinding;
        }

        void setData(ChatMessage chatMessage, Bitmap receiverProfileImage){
            binding.textMessage.setText(MarkdownUtils.formatMarkdown(binding.getRoot().getContext(), chatMessage.message));
            binding.textDateTime.setText(chatMessage.dateTime);
            if(receiverProfileImage != null) {
                binding.imageProfile.setImageBitmap(receiverProfileImage);
            }
            if (chatMessage.isFlagged) {
                binding.imageWarning.setVisibility(android.view.View.VISIBLE);
            } else {
                binding.imageWarning.setVisibility(android.view.View.GONE);
            }
        }
    }
}
