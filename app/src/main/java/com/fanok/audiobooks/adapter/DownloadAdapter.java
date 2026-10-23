package com.fanok.audiobooks.adapter;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.StyleSpan;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.OptIn;
import androidx.core.content.ContextCompat;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.offline.Download;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.fanok.audiobooks.R;
import com.fanok.audiobooks.databinding.ItemDownloadBinding;
import com.fanok.audiobooks.pojo.DownloadItem;
import com.squareup.picasso.Picasso;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Rows of the Downloads screen. The adapter holds an immutable snapshot that is replaced as a whole; selection and
 * expansion are kept by the row's stable id, so a refresh never moves them to another book.
 */
@OptIn(markerClass = UnstableApi.class)
public class DownloadAdapter extends RecyclerView.Adapter<DownloadAdapter.ViewHolder> {

    private List<DownloadItem> itemList = Collections.emptyList();
    private final Set<String> selectedIds = new LinkedHashSet<>();
    private final Set<String> expandedIds = new HashSet<>();

    public interface OnDownloadControlClickListener {
        void onRestartClick(DownloadItem item);
        void onPauseClick(DownloadItem item);
        void onResumeClick(DownloadItem item);
        void onOpenClick(DownloadItem item);
    }

    public interface OnItemSelectionListener {
        void onSelectionChanged();
    }
    private OnItemSelectionListener selectionListener;

    public void setOnItemSelectionListener(OnItemSelectionListener listener) {
        this.selectionListener = listener;
    }

    private final OnDownloadControlClickListener mListener;

    public DownloadAdapter(OnDownloadControlClickListener listener) {
        mListener = listener;
    }

    /** The selected rows of the current snapshot, resolved now into an immutable list. */
    @NonNull
    public List<DownloadItem> getSelectedItems() {
        List<DownloadItem> selected = new ArrayList<>();
        for (DownloadItem item : itemList) {
            if (selectedIds.contains(item.getId())) selected.add(item);
        }
        return Collections.unmodifiableList(selected);
    }

    public int getSelectedItemsSize() {
        return selectedIds.size();
    }

    @Nullable
    public DownloadItem getItem(int position) {
        return position >= 0 && position < itemList.size() ? itemList.get(position) : null;
    }

    /** Redraws the row of {@code item} (e.g. after a cancelled swipe), wherever it is now. */
    public void restore(@NonNull DownloadItem item) {
        for (int i = 0; i < itemList.size(); i++) {
            if (itemList.get(i).getId().equals(item.getId())) {
                notifyItemChanged(i);
                return;
            }
        }
    }

    /** Shows a new snapshot; rows are matched by id, and selections of rows that are gone are dropped. */
    public void submit(@NonNull List<DownloadItem> snapshot) {
        final List<DownloadItem> oldList = itemList;
        final List<DownloadItem> newList = Collections.unmodifiableList(new ArrayList<>(snapshot));
        Set<String> ids = new HashSet<>();
        for (DownloadItem item : newList) ids.add(item.getId());
        boolean selectionChanged = selectedIds.retainAll(ids);
        expandedIds.retainAll(ids);

        DiffUtil.DiffResult diff = DiffUtil.calculateDiff(new DownloadDiffCallback(oldList, newList));
        itemList = newList;
        diff.dispatchUpdatesTo(this);
        if (selectionChanged) {
            notifySelectionChanged();
        }
    }

    public void selectAll() {
        if (selectedIds.size() == itemList.size()) return;
        for (DownloadItem item : itemList) selectedIds.add(item.getId());
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    public void clearSelection() {
        selectedIds.clear();
        notifyDataSetChanged();
        notifySelectionChanged();
    }

    private void notifySelectionChanged() {
        if (selectionListener != null) {
            selectionListener.onSelectionChanged();
        }
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        ItemDownloadBinding binding = ItemDownloadBinding.inflate(inflater, parent, false);
        return new ViewHolder(binding);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        holder.bind(itemList.get(position), mListener);
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    public class ViewHolder extends RecyclerView.ViewHolder {
        private final ItemDownloadBinding binding;

        public ViewHolder(@NonNull ItemDownloadBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }


        public void bind (DownloadItem item, OnDownloadControlClickListener listener) {

            String fileIcon = item.getFileIcon();
            if (fileIcon != null && !fileIcon.trim().isEmpty()) {
                try {
                    if (fileIcon.startsWith("content://")) {
                        Picasso.get()
                                .load(android.net.Uri.parse(fileIcon))
                                .error(android.R.drawable.ic_menu_gallery)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .into(binding.imageFileType);
                    } else if (fileIcon.startsWith("http://") || fileIcon.startsWith("https://")) {
                        Picasso.get()
                                .load(fileIcon)
                                .error(android.R.drawable.ic_menu_gallery)
                                .placeholder(android.R.drawable.ic_menu_gallery)
                                .into(binding.imageFileType);
                    } else {
                        java.io.File imgFile = new java.io.File(fileIcon);
                        if (imgFile.exists()) {
                            Picasso.get()
                                    .load(imgFile)
                                    .error(android.R.drawable.ic_menu_gallery)
                                    .placeholder(android.R.drawable.ic_menu_gallery)
                                    .into(binding.imageFileType);
                        } else {
                            binding.imageFileType.setImageResource(android.R.drawable.ic_menu_gallery);
                        }
                    }
                } catch (Exception e) {
                    binding.imageFileType.setImageResource(android.R.drawable.ic_menu_gallery);
                }
            } else {
                binding.imageFileType.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            setIconForDetailTextView(binding.textBookName, R.drawable.ic_book_open_outline, 12);
            setIconForDetailTextView(binding.textAuthor, R.drawable.ic_autor, 12);
            setIconForDetailTextView(binding.textPerformer, R.drawable.ic_artist, 12);
            setIconForDetailTextView(binding.textChapterName, R.drawable.ic_bookmark_outline, 12);
            setIconForDetailTextView(binding.textExported, R.drawable.ic_download, 12);
            setIconForDetailTextView(binding.sourceName, R.drawable.ic_web, 12);

            if (item.getProgress()>0) {
                binding.progressDownload.setIndeterminate(false);
                binding.progressDownload.setProgress(item.getProgress());
            }else {
                binding.progressDownload.setIndeterminate(true);
            }

            Context context = itemView.getContext();
            String title = item.getBookName() == null || item.getBookName().isEmpty() ? item.getBookUrl() : item.getBookName();
            binding.textFileName.setText(title);

            if (item.getBookName() == null || item.getBookName().isEmpty()) {
                binding.textBookName.setVisibility(View.GONE);
            } else {
                binding.textBookName.setVisibility(View.VISIBLE);
                binding.textBookName.setText(getStyledLabelAndValue("Книга:", item.getBookName()));
            }

            if (item.getAuthor() == null || item.getAuthor().isEmpty()) {
                binding.textAuthor.setVisibility(View.GONE);
            } else {
                binding.textAuthor.setVisibility(View.VISIBLE);
                binding.textAuthor.setText(getStyledLabelAndValue("Автор:", item.getAuthor()));
            }

            if (item.getReader() == null || item.getReader().isEmpty()) {
                binding.textPerformer.setVisibility(View.GONE);
            } else {
                binding.textPerformer.setVisibility(View.VISIBLE);
                binding.textPerformer.setText(getStyledLabelAndValue("Исполнитель:", item.getReader()));
            }

            binding.textChapterName.setVisibility(View.VISIBLE);
            binding.textChapterName.setText(getStyledLabelAndValue(context.getString(R.string.download_chapters_label),
                    context.getString(R.string.download_chapters_value, item.getCompleted(), item.getTotal(), item.getSelected())));

            binding.textExported.setVisibility(View.VISIBLE);
            binding.textExported.setText(getStyledLabelAndValue(context.getString(R.string.download_exported_label),
                    item.getExportedChapters().isEmpty() ? context.getString(R.string.download_exported_none)
                            : TextUtils.join(", ", item.getExportedChapters())));

            if (item.getSource() == null || item.getSource().isEmpty()) {
                binding.sourceName.setVisibility(View.GONE);
            } else {
                binding.sourceName.setVisibility(View.VISIBLE);
                binding.sourceName.setText(getStyledLabelAndValue("Источник:", item.getSource()));
            }

            binding.buttonControlDownload.setOnClickListener(v -> {
                if (listener == null) return;
                switch (item.getStatus()) {
                    case Download.STATE_DOWNLOADING:
                    case Download.STATE_QUEUED:
                        listener.onPauseClick(item);
                        break;
                    case Download.STATE_STOPPED:
                        listener.onResumeClick(item);
                        break;
                    case Download.STATE_FAILED:
                        listener.onRestartClick(item);
                        break;
                    case Download.STATE_COMPLETED:
                        listener.onOpenClick(item);
                        break;
                    default:
                        break;
                }
            });


            final String id = item.getId();
            itemView.setOnClickListener(v -> {
                boolean isVisible = binding.layoutDetails.getVisibility() == View.VISIBLE;
                binding.layoutDetails.setVisibility(isVisible ? View.GONE : View.VISIBLE);
                if (isVisible) expandedIds.remove(id); else expandedIds.add(id);
            });
            binding.layoutDetails.setVisibility(expandedIds.contains(id) ? View.VISIBLE : View.GONE);


            binding.checkboxSelect.setOnClickListener(v -> {
                if (!selectedIds.isEmpty()) {
                    toggleSelection(id);
                }
            });


            itemView.setOnLongClickListener(v -> {
                toggleSelection(id);
                return true;
            });


            binding.checkboxSelect.setVisibility(selectedIds.isEmpty() ? View.GONE : View.VISIBLE);
            binding.checkboxSelect.setChecked(selectedIds.contains(id));


            updateUiForStatus(item);
        }


            private void toggleSelection(String id) {
            if (!selectedIds.remove(id)) {
                selectedIds.add(id);
            }
            notifyDataSetChanged();
            notifySelectionChanged();
        }

        private void updateUiForStatus(DownloadItem item) {
            switch (item.getStatus()) {
                case Download.STATE_DOWNLOADING:
                case Download.STATE_QUEUED:
                    binding.progressDownload.setVisibility(View.VISIBLE);
                    binding.buttonControlDownload.setImageBitmap(getBitMapWithFixedSize(R.drawable.ic_pause, 24));
                    break;
                case Download.STATE_STOPPED:
                    binding.progressDownload.setVisibility(View.VISIBLE);
                    binding.buttonControlDownload.setImageBitmap(getBitMapWithFixedSize(R.drawable.ic_play, 24));
                    break;
                case Download.STATE_COMPLETED:
                    // A book is complete only when every chapter is; otherwise the bar shows how much of it is.
                    binding.progressDownload.setVisibility(item.isComplete() ? View.INVISIBLE : View.VISIBLE);
                    binding.buttonControlDownload.setImageBitmap(getBitMapWithFixedSize(R.drawable.ic_is_check, 24));
                    break;
                case Download.STATE_FAILED:
                    binding.progressDownload.setVisibility(View.INVISIBLE);
                    binding.buttonControlDownload.setImageBitmap(getBitMapWithFixedSize(R.drawable.ic_error, 24));
                    break;
                default:
                    binding.progressDownload.setVisibility(View.VISIBLE);
                    binding.buttonControlDownload.setImageBitmap(getBitMapWithFixedSize(R.drawable.ic_pause, 24));
                    break;
            }
        }


        @Nullable
        private Bitmap getBitMapWithFixedSize(int drawableResId, int targetSizeInDp) {
            Context context = itemView.getContext();
            Drawable originalDrawable = ContextCompat.getDrawable(context, drawableResId);

            if (originalDrawable == null) {
                return null;
            }
            float density = context.getResources().getDisplayMetrics().density;
            int targetSizeInPixels = (int) (targetSizeInDp * density);
            Bitmap bitmap = Bitmap.createBitmap(targetSizeInPixels, targetSizeInPixels, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            originalDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            originalDrawable.draw(canvas);
            return bitmap;
        }


        private void setIconForDetailTextView(TextView textView, int drawableResId, int sizeInDp) {
            Context context = itemView.getContext();
            Drawable originalDrawable = ContextCompat.getDrawable(context, drawableResId);

            if (originalDrawable == null) {
                return;
            }

            float density = context.getResources().getDisplayMetrics().density;
            int sizeInPixels = (int) (sizeInDp * density);
            originalDrawable.setBounds(0, 0, sizeInPixels, sizeInPixels);
            textView.setCompoundDrawables(originalDrawable, null, null, null);
        }

        private Spannable getStyledLabelAndValue(String label, String value) {
            SpannableStringBuilder builder = new SpannableStringBuilder();

            // Добавляем метку и применяем к ней жирный стиль
            builder.append(label);
            builder.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            // Добавляем пробел и значение
            builder.append("   ");
            builder.append(value);

            return builder;
        }



    }

    private static class DownloadDiffCallback extends DiffUtil.Callback {

        private final List<DownloadItem> oldList;
        private final List<DownloadItem> newList;

        DownloadDiffCallback(List<DownloadItem> oldList, List<DownloadItem> newList) {
            this.oldList = oldList;
            this.newList = newList;
        }

        @Override
        public int getOldListSize() {
            return oldList.size();
        }

        @Override
        public int getNewListSize() {
            return newList.size();
        }

        @Override
        public boolean areItemsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).getId().equals(newList.get(newItemPosition).getId());
        }

        @Override
        public boolean areContentsTheSame(int oldItemPosition, int newItemPosition) {
            return oldList.get(oldItemPosition).sameContents(newList.get(newItemPosition));
        }
    }

}
