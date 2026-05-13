package com.example.demo.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.Switch;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.demo.R;
import com.example.demo.data.Persona;

import java.util.ArrayList;
import java.util.List;

public class PersonaManagementAdapter extends RecyclerView.Adapter<PersonaManagementAdapter.PersonaViewHolder> {

    private List<Persona> personaList = new ArrayList<>();
    private final PersonaActionListener listener;

    public interface PersonaActionListener {
        void onDeleteClick(Persona persona);
        void onToggleActive(Persona persona, boolean isActive);
    }

    public PersonaManagementAdapter(PersonaActionListener listener) {
        this.listener = listener;
    }

    public void setPersonaList(List<Persona> newPersonaList) {
        this.personaList = newPersonaList;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public PersonaViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_persona_management, parent, false);
        return new PersonaViewHolder(view, listener);
    }

    @Override
    public void onBindViewHolder(@NonNull PersonaViewHolder holder, int position) {
        Persona persona = personaList.get(position);
        holder.bind(persona);
    }

    @Override
    public int getItemCount() {
        return personaList.size();
    }

    public static class PersonaViewHolder extends RecyclerView.ViewHolder {
        private final ImageView personaAvatar;
        private final TextView personaName;
        private final TextView personaPersonality;
        private final Switch activeSwitch;
        private final ImageButton deleteButton;
        private Persona currentPersona;

        public PersonaViewHolder(@NonNull View itemView, PersonaActionListener listener) {
            super(itemView);
            personaAvatar = itemView.findViewById(R.id.persona_avatar_management);
            personaName = itemView.findViewById(R.id.persona_name_management);
            personaPersonality = itemView.findViewById(R.id.persona_personality_management);
            activeSwitch = itemView.findViewById(R.id.persona_active_switch);
            deleteButton = itemView.findViewById(R.id.btn_delete_persona);

            deleteButton.setOnClickListener(v -> {
                if (listener != null && currentPersona != null) {
                    listener.onDeleteClick(currentPersona);
                }
            });

            activeSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                if (buttonView.isPressed() && listener != null && currentPersona != null) {
                    listener.onToggleActive(currentPersona, isChecked);
                }
            });
        }

        public void bind(Persona persona) {
            this.currentPersona = persona;

            personaName.setText(persona.getName());
            personaPersonality.setText(persona.getPersonality());

            activeSwitch.setChecked(persona.isActive());

            if (persona.getAvatarUrl() != null && !persona.getAvatarUrl().isEmpty()) {
                Uri avatarUri = Uri.parse(persona.getAvatarUrl());
                Glide.with(itemView.getContext())
                        .load(avatarUri)
                        .placeholder(R.drawable.default_avatar)
                        .error(R.drawable.default_avatar)
                        .into(personaAvatar);
            } else {
                personaAvatar.setImageResource(R.drawable.default_avatar);
            }
        }
    }
}