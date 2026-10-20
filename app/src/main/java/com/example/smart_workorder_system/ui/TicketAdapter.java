package com.example.smart_workorder_system.ui;

import android.view.LayoutInflater;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import com.example.smart_workorder_system.data.Models;
import com.example.smart_workorder_system.databinding.ItemTicketBinding;
import java.util.function.Consumer;

public class TicketAdapter extends ListAdapter<Models.Ticket,TicketAdapter.Holder> {
    private final Consumer<Models.Ticket> click;
    public TicketAdapter(Consumer<Models.Ticket> click) {
        super(new DiffUtil.ItemCallback<Models.Ticket>() {
            @Override public boolean areItemsTheSame(@NonNull Models.Ticket a,@NonNull Models.Ticket b) { return a.id==b.id; }
            @Override public boolean areContentsTheSame(@NonNull Models.Ticket a,@NonNull Models.Ticket b) {
                return java.util.Objects.equals(a.updatedAt,b.updatedAt) && java.util.Objects.equals(a.status,b.status);
            }
        }); this.click=click;
    }
    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent,int type) {
        return new Holder(ItemTicketBinding.inflate(LayoutInflater.from(parent.getContext()),parent,false));
    }
    @Override public void onBindViewHolder(@NonNull Holder holder,int position) {
        Models.Ticket t=getItem(position); ItemTicketBinding b=holder.binding;
        b.category.setText(t.category+" · "+t.priority); b.summary.setText(t.summary); Ui.badge(b.status,t.status);
        b.metadata.setText(Ui.date(t.createdAt)+"  ·  "+Ui.orDash(t.location)); b.number.setText(t.number);
        b.getRoot().setOnClickListener(v -> click.accept(t));
    }
    static class Holder extends RecyclerView.ViewHolder {
        final ItemTicketBinding binding;
        Holder(ItemTicketBinding binding) { super(binding.getRoot());this.binding=binding; }
    }
}
