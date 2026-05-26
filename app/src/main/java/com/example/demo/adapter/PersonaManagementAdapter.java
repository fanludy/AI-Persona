package com.example.demo.adapter;

import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
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

    // 1. 角色列表数据源
    private List<Persona> personaList = new ArrayList<>();

    // 2. 文档列表映射缓存 (PersonaId -> 文档名列表)
    private java.util.Map<Integer, List<String>> documentsMap = new java.util.HashMap<>();

    private final PersonaActionListener listener;

    public interface PersonaActionListener {
        void onDeleteClick(Persona persona);
        void onToggleActive(Persona persona, boolean isActive);
        void onAddKnowledgeClick(Persona persona);
        void onDeleteDocumentClick(int personaId, String docName);
        // 💡 新增：Markdown 开关切换回调
        void onToggleMarkdown(Persona persona, boolean isEnabled);

        void onAvatarClick(Persona persona);
        void onToggleColloquial(Persona persona, boolean isEnabled);
        void onWordLimitChanged(Persona persona, boolean isEnabled, int limit);
    }

    public PersonaManagementAdapter(PersonaActionListener listener) {
        this.listener = listener;
    }

    public void setPersonaList(List<Persona> newPersonaList) {
        this.personaList = newPersonaList;
        notifyDataSetChanged();
    }

    public void setPersonaDocuments(java.util.Map<Integer, List<String>> map) {
        this.documentsMap = map;
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
        holder.bind(personaList.get(position));
    }

    @Override
    public int getItemCount() {
        return personaList != null ? personaList.size() : 0;
    }

    // 💡 注意这里没有 static，方便访问外部的 documentsMap
    public class PersonaViewHolder extends RecyclerView.ViewHolder {
        private final ImageView personaAvatar;
        private final TextView personaName;
        private final TextView personaPersonality;
        private final Switch activeSwitch;
        private final ImageButton deleteButton;
        private final LinearLayout llDocumentsContainer;
        private final View docDivider;
        private Persona currentPersona;
        private final Switch markdownSwitch; // 💡 新增变量

        private final Switch colloquialSwitch;
        private final Switch wordLimitSwitch;
        private final android.widget.EditText etWordLimit;
        private final android.widget.FrameLayout avatarContainer;

        public PersonaViewHolder(@NonNull View itemView, PersonaActionListener listener) {
            super(itemView);
            personaAvatar = itemView.findViewById(R.id.persona_avatar_management);
            personaName = itemView.findViewById(R.id.persona_name_management);
            personaPersonality = itemView.findViewById(R.id.persona_personality_management);
            activeSwitch = itemView.findViewById(R.id.persona_active_switch);
            deleteButton = itemView.findViewById(R.id.btn_delete_persona);
            llDocumentsContainer = itemView.findViewById(R.id.ll_documents_container);
            docDivider = itemView.findViewById(R.id.doc_divider);
            markdownSwitch = itemView.findViewById(R.id.persona_markdown_switch);
            colloquialSwitch = itemView.findViewById(R.id.persona_colloquial_switch);
            wordLimitSwitch = itemView.findViewById(R.id.persona_word_limit_switch);
            etWordLimit = itemView.findViewById(R.id.et_word_limit);
            avatarContainer = itemView.findViewById(R.id.avatar_container);

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

            markdownSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
                // 确保是用户手动点击，而不是 RecyclerView 复用滑动时触发的
                if (buttonView.isPressed() && listener != null && currentPersona != null) {
                    listener.onToggleMarkdown(currentPersona, isChecked);
                }
            });

            // 💡 1. 头像点击事件
            avatarContainer.setOnClickListener(v -> {
                if (listener != null && currentPersona != null) {
                    listener.onAvatarClick(currentPersona);
                }
            });

            // 💡 2. 口语化开关
            colloquialSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                if (btn.isPressed() && listener != null && currentPersona != null) {
                    listener.onToggleColloquial(currentPersona, isChecked);
                }
            });

            // 💡 3. 字数限制开关联动
            wordLimitSwitch.setOnCheckedChangeListener((btn, isChecked) -> {
                etWordLimit.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                if (btn.isPressed() && listener != null && currentPersona != null) {
                    int limit = etWordLimit.getText().toString().isEmpty() ? 100 : Integer.parseInt(etWordLimit.getText().toString());
                    listener.onWordLimitChanged(currentPersona, isChecked, limit);
                }
            });

            // 💡 4. 字数输入框失去焦点时保存
            etWordLimit.addTextChangedListener(new android.text.TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}

                @Override
                public void afterTextChanged(android.text.Editable s) {
                    // 当且仅当用户真正手动打字输入、且当前角色不为空时触发
                    if (etWordLimit.hasFocus() && listener != null && currentPersona != null) {
                        try {
                            String inputStr = s.toString().trim();
                            // 防止用户删成空字符串导致崩溃，给个默认下限值 10 字
                            int limit = inputStr.isEmpty() ? 10 : Integer.parseInt(inputStr);

                            // 💡 修正：使用 currentPersona 同步内存并保存
                            currentPersona.setWordLimit(limit);
                            listener.onWordLimitChanged(currentPersona, wordLimitSwitch.isChecked(), limit);
                        } catch (NumberFormatException e) {
                            android.util.Log.e("PersonaAdapter", "字数转换异常: " + e.getMessage());
                        }
                    }
                }
            });

            ImageButton btnKnowledgeBase = itemView.findViewById(R.id.btn_knowledge_base);
            if (btnKnowledgeBase != null) {
                btnKnowledgeBase.setOnClickListener(v -> {
                    if (listener != null && currentPersona != null) {
                        listener.onAddKnowledgeClick(currentPersona);
                    }
                });
            }
        }

        public void bind(Persona persona) {
            this.currentPersona = persona;

            personaName.setText(persona.getName());
            personaPersonality.setText(persona.getPersonality());
            activeSwitch.setChecked(persona.isActive());
            markdownSwitch.setChecked(persona.isMarkdownEnabled());

            colloquialSwitch.setChecked(persona.isColloquialEnabled());
            wordLimitSwitch.setChecked(persona.isWordLimitEnabled());
            etWordLimit.setText(String.valueOf(persona.getWordLimit()));
            etWordLimit.setVisibility(persona.isWordLimitEnabled() ? View.VISIBLE : View.GONE);

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

            // 文档列表动态渲染
            llDocumentsContainer.removeAllViews();
            List<String> docList = documentsMap.get(persona.getId());

            if (docList != null && !docList.isEmpty()) {
                llDocumentsContainer.setVisibility(View.VISIBLE);
                if (docDivider != null) docDivider.setVisibility(View.VISIBLE);

                for (String docName : docList) {
                    View docView = LayoutInflater.from(itemView.getContext()).inflate(R.layout.item_document_line, llDocumentsContainer, false);
                    TextView tvDocName = docView.findViewById(R.id.tv_doc_name);
                    ImageButton btnDeleteDoc = docView.findViewById(R.id.btn_delete_doc);

                    tvDocName.setText(docName);

                    btnDeleteDoc.setOnClickListener(v -> {
                        if (listener != null) {
                            listener.onDeleteDocumentClick(persona.getId(), docName);
                        }
                    });
                    llDocumentsContainer.addView(docView);
                }
            } else {
                llDocumentsContainer.setVisibility(View.GONE);
                if (docDivider != null) docDivider.setVisibility(View.GONE);
            }
        }
    }
}