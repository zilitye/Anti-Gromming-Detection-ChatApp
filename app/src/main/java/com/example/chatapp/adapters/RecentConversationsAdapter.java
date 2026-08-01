package com.example.chatapp.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatapp.databinding.ItemContainerRecentConversionBinding;
import com.example.chatapp.listeners.ConversionListener;
import com.example.chatapp.models.ChatMessage;
import com.example.chatapp.models.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class RecentConversationsAdapter extends RecyclerView.Adapter<RecentConversationsAdapter.ConversionViewHolder>{

    /** Source of truth, kept in sync with Firestore by the activity. */
    private final List<ChatMessage> allConversations;
    /** What is actually shown, a possibly-filtered view of allConversations. */
    private final List<ChatMessage> displayedConversations = new ArrayList<>();
    private final ConversionListener conversionListener;
    private String currentQuery = "";

    public RecentConversationsAdapter(List<ChatMessage> chatMessages, ConversionListener conversionListener){
        this.allConversations = chatMessages;
        this.displayedConversations.addAll(chatMessages);
        this.conversionListener = conversionListener;
    }

    @NonNull
    @Override
    public ConversionViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new ConversionViewHolder(
                ItemContainerRecentConversionBinding.inflate(
                        LayoutInflater.from(parent.getContext()),
                        parent,
                        false
                )
        );
    }

    @Override
    public void onBindViewHolder(@NonNull ConversionViewHolder holder, int position) {
        holder.setData(displayedConversations.get(position));
    }

    @Override
    public int getItemCount() {
        return displayedConversations.size();
    }

    /** Call after allConversations has been mutated in place (e.g. by a Firestore listener). */
    public void refresh() {
        applyFilter();
        notifyDataSetChanged();
    }

    /** Filters the visible list by contact name without losing the underlying conversation data. */
    public void filter(String query) {
        currentQuery = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        applyFilter();
        notifyDataSetChanged();
    }

    private void applyFilter() {
        displayedConversations.clear();
        if (currentQuery.isEmpty()) {
            displayedConversations.addAll(allConversations);
        } else {
            for (ChatMessage message : allConversations) {
                if (message.conversionName != null &&
                        message.conversionName.toLowerCase(Locale.getDefault()).contains(currentQuery)) {
                    displayedConversations.add(message);
                }
            }
        }
    }

    public boolean isSourceEmpty() {
        return allConversations.isEmpty();
    }

    class ConversionViewHolder extends RecyclerView.ViewHolder{
        ItemContainerRecentConversionBinding binding;
        ConversionViewHolder(ItemContainerRecentConversionBinding itemContainerRecentConversionBinding){
            super(itemContainerRecentConversionBinding.getRoot());
            binding = itemContainerRecentConversionBinding;
        }

        void setData(ChatMessage chatMessage){
            binding.imageProfile.setImageBitmap(getConversionImage(chatMessage.conversionImage));
            binding.textName.setText(chatMessage.conversionName);
            binding.textRecentMessage.setText(chatMessage.message);
            binding.getRoot().setOnClickListener(v -> {
                User user = new User();
                user.id = chatMessage.conversionId;
                user.name = chatMessage.conversionName;
                user.image = chatMessage.conversionImage;
                conversionListener.onConversionClicked(user);
            });
        }
    }

    private Bitmap getConversionImage(String encodedImage){
        byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
