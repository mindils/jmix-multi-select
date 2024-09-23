package com.company.jmixmultiselect.action;

import com.google.common.base.Strings;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.progressbar.ProgressBar;
import io.jmix.core.MessageTools;
import io.jmix.flowui.action.ActionType;
import io.jmix.flowui.backgroundtask.BackgroundTask;
import io.jmix.flowui.backgroundtask.BackgroundTaskHandler;
import io.jmix.flowui.backgroundtask.BackgroundWorker;
import io.jmix.flowui.backgroundtask.TaskLifeCycle;
import io.jmix.flowui.component.ListDataComponent;
import io.jmix.flowui.data.grid.EntityDataGridItems;
import io.jmix.flowui.download.ByteArrayDownloadDataProvider;
import io.jmix.gridexportflowui.action.ExcelExportAction;
import io.jmix.gridexportflowui.exporter.ExportMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static io.jmix.flowui.download.DownloadFormat.XLSX;

@ActionType(AsyncExcelExportAction.ID)
public class AsyncExcelExportAction extends ExcelExportAction {
    private static final Logger log = LoggerFactory.getLogger(AsyncExcelExportAction.class);
    public static final String ID = "async_excel_export";

    private BackgroundWorker backgroundWorker;
    private MessageTools messageTools;
    private Environment environment;


    private String fileName;

    public AsyncExcelExportAction() {
    }

    public AsyncExcelExportAction(String id) {
        super(id);
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        super.setApplicationContext(applicationContext);

        backgroundWorker = applicationContext.getBean(BackgroundWorker.class);
        messageTools = applicationContext.getBean(MessageTools.class);
        environment = applicationContext.getBean(Environment.class);
    }

    @Override
    public void setFileName(String fileName) {
        super.setFileName(fileName);

        this.fileName = fileName;
    }

    @Nullable
    public Supplier<Map> getCustomSelectionSupplier() {
        return ((ExtExcelExporter) dataGridExporter).getCustomSelectionSupplier();
    }

    public void setCustomSelectionSupplier(@Nullable Supplier<Map> customSelectionSupplier) {
        ((ExtExcelExporter) dataGridExporter).setCustomSelectionSupplier(customSelectionSupplier);
    }

    protected String getMessage(String id) {
        // Указываем пакет от ExcelExportAction, чтобы найти message keys для локализации.
        // В принципе можно в самом проекте указать message key с пакетом от AsyncExcelExportAction.
        return messages.getMessage(ExcelExportAction.class, id);
    }


    @Override
    protected void doExport(ExportMode exportMode) {
        if (getTarget() instanceof Grid grid) {
            doAsyncExport(grid, exportMode);
        } else {
            throw new UnsupportedOperationException("Unsupported component for export");
        }
    }

    private void doAsyncExport(Grid grid, ExportMode exportMode) {
        String fileName = this.fileName;
        if (Strings.isNullOrEmpty(fileName)) {
            ListDataComponent<?> listDataComponent = (ListDataComponent<?>) grid;

            EntityDataGridItems<?> items = (EntityDataGridItems<?>) listDataComponent.getItems();
            if (items == null) {
                throw new IllegalStateException("Grid's items is null");
            }

            fileName = messageTools.getEntityCaption(items.getEntityMetaClass());
        }

        fileName = fileName + "." + XLSX.getFileExt();

        Dialog dialog = createDialog(fileName);

        int timeout = environment.getProperty("asyncExcelExportAction.timeout", Integer.class, 30);

        ExportToExcelTask exportToExcelTask =
                new ExportToExcelTask(timeout, (ExtExcelExporter) dataGridExporter, exportMode, grid, fileName, dialog);

        BackgroundTaskHandler<ByteArrayDownloadDataProvider> taskHandler = backgroundWorker.handle(exportToExcelTask);

        showDialog(taskHandler, dialog);
    }

    protected Dialog createDialog(String fileName) {
        Dialog backgroundTaskDialog = new Dialog();
        backgroundTaskDialog.setHeaderTitle(messages.getMessage(getClass(), "asyncExcelExportAction.dialog.header"));
        backgroundTaskDialog.setCloseOnOutsideClick(false);
        backgroundTaskDialog.setCloseOnEsc(false);

        VerticalLayout content = new VerticalLayout();
        ProgressBar progressBar = new ProgressBar();
        progressBar.setIndeterminate(true);
        content.add(new Span(messages.formatMessage(getClass(), "asyncExcelExportAction.dialog.content", fileName)));
        content.add(progressBar);

        backgroundTaskDialog.add(content);
        return backgroundTaskDialog;
    }

    private void showDialog(BackgroundTaskHandler<ByteArrayDownloadDataProvider> taskHandler, Dialog dialog) {
        Button cancelButton = new Button(messages.getMessage(getClass(), "asyncExcelExportAction.dialog.cancel"));
        cancelButton.addClickListener(event -> {
            dialog.close();
            taskHandler.cancel();
        });
        cancelButton.setIcon(VaadinIcon.BAN.create());
        dialog.getFooter().add(cancelButton);

        dialog.open();
        taskHandler.execute();
    }

    private class ExportToExcelTask extends BackgroundTask<Void, ByteArrayDownloadDataProvider> {
        private final ExtExcelExporter excelExporter;
        private final ExportMode exportMode;
        private final Grid<Object> grid;
        private final String fileName;
        private final Dialog dialog;

        public ExportToExcelTask(int timeout,
                                 ExtExcelExporter excelExporter,
                                 ExportMode exportMode,
                                 Grid<Object> grid,
                                 String fileName,
                                 Dialog dialog) {
            super(timeout);

            this.excelExporter = excelExporter;
            this.exportMode = exportMode;
            this.grid = grid;
            this.fileName = fileName;
            this.dialog = dialog;
        }

        @Nullable
        @Override
        public ByteArrayDownloadDataProvider run(TaskLifeCycle taskLifeCycle) throws Exception {
            ByteArrayDownloadDataProvider byteArrayDownloadDataProvider = excelExporter.getDataGridBytes(grid, exportMode);

            if (taskLifeCycle.isInterrupted() || taskLifeCycle.isCancelled()) {
                return null;
            }

            return byteArrayDownloadDataProvider;
        }

        @Override
        public void done(@Nullable ByteArrayDownloadDataProvider result) {
            dialog.close();

            if (result == null) {
                return;
            }

            downloader.download(result, fileName, XLSX);
        }

        @Override
        public boolean handleTimeoutException() {
            dialog.close();
            log.warn("Task is finished by timeout");
            return super.handleTimeoutException();
        }

        @Override
        public boolean handleException(Exception ex) {
            dialog.close();
            log.error("Exception while performing a task", ex);
            return super.handleException(ex);
        }
    }
}
