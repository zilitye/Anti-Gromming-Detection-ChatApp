package com.example.chatapp.adapters;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.view.LayoutInflater;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.chatapp.databinding.ItemContainerUserBinding;
import com.example.chatapp.listeners.UserListener;
import com.example.chatapp.models.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class UsersAdapter extends RecyclerView.Adapter<UsersAdapter.UserViewHolder>{

    private final List<User> allUsers;
    private final List<User> displayedUsers = new ArrayList<>();
    private final UserListener userListener;
    private String currentQuery = "";

    public UsersAdapter(List<User> users, UserListener userListener) {
        this.allUsers = users;
        this.displayedUsers.addAll(users);
        this.userListener = userListener;
    }

    @NonNull
    @Override
    public UserViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        ItemContainerUserBinding itemContainerUserBinding = ItemContainerUserBinding.inflate(
                LayoutInflater.from(parent.getContext()),
                parent,
                false
        );
        return new UserViewHolder(itemContainerUserBinding);
    }

    @Override
    public void onBindViewHolder(@NonNull UserViewHolder holder, int position) {
        holder.setUserData(displayedUsers.get(position));
    }

    @Override
    public int getItemCount() {
        return displayedUsers.size();
    }

    /** Filters the visible list by name or email without losing the original data set. */
    public void filter(String query) {
        currentQuery = query == null ? "" : query.trim().toLowerCase(Locale.getDefault());
        displayedUsers.clear();
        if (currentQuery.isEmpty()) {
            displayedUsers.addAll(allUsers);
        } else {
            for (User user : allUsers) {
                boolean nameMatches = user.name != null && user.name.toLowerCase(Locale.getDefault()).contains(currentQuery);
                boolean emailMatches = user.email != null && user.email.toLowerCase(Locale.getDefault()).contains(currentQuery);
                if (nameMatches || emailMatches) {
                    displayedUsers.add(user);
                }
            }
        }
        notifyDataSetChanged();
    }

    public boolean isEmpty() {
        return displayedUsers.isEmpty();
    }

    class UserViewHolder extends RecyclerView.ViewHolder{
        ItemContainerUserBinding binding;
        UserViewHolder(ItemContainerUserBinding itemContainerUserBinding){
            super(itemContainerUserBinding.getRoot());
            binding = itemContainerUserBinding;
        }
        void setUserData(User user){
            binding.textName.setText(user.name);
            binding.textEmail.setText(user.email);
            binding.imageProfile.setImageBitmap(getUserImage(user.image));
            binding.getRoot().setOnClickListener(v -> userListener.onUserClicked(user));
        }
    }

    private Bitmap getUserImage(String encodedImage){
        byte[] bytes = Base64.decode(encodedImage, Base64.DEFAULT);
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
    }
}
