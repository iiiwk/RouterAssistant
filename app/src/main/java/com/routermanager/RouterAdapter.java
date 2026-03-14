package com.routermanager;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class RouterAdapter extends RecyclerView.Adapter<RouterAdapter.ViewHolder> {

    public interface OnRouterClickListener {
        void onRouterClick(RouterInfo router);
        void onRouterDelete(RouterInfo router, int position);
        void onRouterLongClick(RouterInfo router, int position);
    }

    private final List<RouterInfo> routers = new ArrayList<>();
    private OnRouterClickListener listener;

    public void setListener(OnRouterClickListener listener) {
        this.listener = listener;
    }

    public void setRouters(List<RouterInfo> list) {
        routers.clear();
        routers.addAll(list);
        notifyDataSetChanged();
    }

    public void removeAt(int position) {
        if (position >= 0 && position < routers.size()) {
            routers.remove(position);
            notifyItemRemoved(position);
        }
    }

    public void updateAt(int position, RouterInfo router) {
        if (position >= 0 && position < routers.size()) {
            routers.set(position, router);
            notifyItemChanged(position);
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_router, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        RouterInfo router = routers.get(position);
        String alias = router.getAlias();
        boolean hasAlias = !alias.isEmpty();

        holder.tvAddress.setText(hasAlias ? alias : router.getAddress());

        String username = router.getUsername();
        String password = router.getPassword();
        String credInfo;
        if (!username.isEmpty()) {
            credInfo = holder.itemView.getContext().getString(R.string.label_user_prefix, username);
        } else if (!password.isEmpty()) {
            credInfo = holder.itemView.getContext().getString(R.string.msg_password_saved);
        } else {
            credInfo = holder.itemView.getContext().getString(R.string.msg_no_saved_credentials);
        }

        if (hasAlias) {
            holder.tvUsername.setText(router.getAddress() + " · " + credInfo);
        } else {
            holder.tvUsername.setText(credInfo);
        }

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onRouterClick(router);
        });

        holder.itemView.setOnLongClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onRouterLongClick(router, pos);
            }
            return true;
        });

        holder.btnDelete.setOnClickListener(v -> {
            int pos = holder.getAdapterPosition();
            if (pos != RecyclerView.NO_POSITION && listener != null) {
                listener.onRouterDelete(router, pos);
            }
        });
    }

    @Override
    public int getItemCount() {
        return routers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView tvAddress;
        final TextView tvUsername;
        final ImageButton btnDelete;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            tvAddress = itemView.findViewById(R.id.tv_address);
            tvUsername = itemView.findViewById(R.id.tv_username);
            btnDelete = itemView.findViewById(R.id.btn_delete);
        }
    }
}
