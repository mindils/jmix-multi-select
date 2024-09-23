package com.company.jmixmultiselect.action;

import com.vaadin.flow.component.grid.Grid;
import io.jmix.core.Id;
import io.jmix.flowui.Notifications;
import io.jmix.flowui.component.ListDataComponent;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.grid.TreeDataGrid;
import io.jmix.flowui.data.grid.ContainerDataGridItems;
import io.jmix.flowui.data.grid.ContainerTreeDataGridItems;
import io.jmix.flowui.download.ByteArrayDownloadDataProvider;
import io.jmix.flowui.download.Downloader;
import io.jmix.gridexportflowui.GridExportProperties;
import io.jmix.gridexportflowui.exporter.ExportMode;
import io.jmix.gridexportflowui.exporter.entitiesloader.AllEntitiesLoader;
import io.jmix.gridexportflowui.exporter.entitiesloader.AllEntitiesLoaderFactory;
import io.jmix.gridexportflowui.exporter.excel.ExcelAutoColumnSizer;
import io.jmix.gridexportflowui.exporter.excel.ExcelExporter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.RichTextString;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.VerticalAlignment;
import org.apache.poi.xssf.usermodel.XSSFRichTextString;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Scope;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Primary
@Component("bgxlsx_ExtExcelExporter")
@Scope(BeanDefinition.SCOPE_PROTOTYPE)
public class ExtExcelExporter extends ExcelExporter {

    private Supplier<Map> customSelectionSupplier;

    public ExtExcelExporter(GridExportProperties gridExportProperties,
                            Notifications notifications,
                            AllEntitiesLoaderFactory allEntitiesLoaderFactory) {
        super(gridExportProperties, notifications, allEntitiesLoaderFactory);
    }

    @Nullable
    public Supplier<Map> getCustomSelectionSupplier() {
        return customSelectionSupplier;
    }

    public void setCustomSelectionSupplier(@Nullable Supplier<Map> customSelectionSupplier) {
        this.customSelectionSupplier = customSelectionSupplier;
    }

    /**
     * CAUTION! Здесь скопирован практически весь метод из
     * `{@link ExcelExporter#exportDataGrid(Downloader, Grid, ExportMode)}`.
     * Единственное чем `getDataGridBytes()` отличается это вместо загрузки через {@link Downloader}, возвращает
     * {@link ByteArrayDownloadDataProvider}.
     */
    public ByteArrayDownloadDataProvider getDataGridBytes(Grid<Object> dataGrid, ExportMode exportMode) {
        createWorkbookWithSheet();
        try {
            createFonts();
            createFormats();

            List<Grid.Column<Object>> columns = dataGrid.getColumns();

            int r = 0;

            Row row = sheet.createRow(r);
            createAutoColumnSizers(columns.size());

            float maxHeight = sheet.getDefaultRowHeightInPoints();

            CellStyle headerCellStyle = wb.createCellStyle();
            headerCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
            for (DataGrid.Column<?> column : columns) {
                String columnHeaderText = getColumnHeaderText(column);

                int countOfReturnSymbols = StringUtils.countMatches(columnHeaderText, "\n");
                if (countOfReturnSymbols > 0) {
                    maxHeight = Math.max(maxHeight, (countOfReturnSymbols + 1) * sheet.getDefaultRowHeightInPoints());
                    headerCellStyle.setWrapText(true);
                }
            }
            row.setHeightInPoints(maxHeight);

            for (int c = 0; c < columns.size(); c++) {
                DataGrid.Column<?> column = columns.get(c);
                String columnHeaderText = getColumnHeaderText(column);

                Cell cell = row.createCell(c);
                RichTextString richTextString = createStringCellValue(columnHeaderText);
                richTextString.applyFont(boldFont);
                cell.setCellValue(richTextString);

                ExcelAutoColumnSizer sizer = new ExcelAutoColumnSizer();
                sizer.notifyCellValue(columnHeaderText, boldFont);
                sizers[c] = sizer;

                cell.setCellStyle(headerCellStyle);
            }

            ContainerDataGridItems<Object> dataGridSource = (ContainerDataGridItems) ((ListDataComponent<Object>) dataGrid).getItems();
            if (dataGridSource == null) {
                throw new IllegalStateException("DataGrid is not bound to data");
            }

            Collection customSelection = customSelectionSupplier == null ? null : customSelectionSupplier.get().values();

            // Используем наш поставщик выделенных данных
            if (exportMode == ExportMode.SELECTED_ROWS && CollectionUtils.isNotEmpty(customSelection)) {
                for (Object item : customSelection) {
                    if (checkIsRowNumberExceed(r)) {
                        break;
                    }

                    createDataGridRow(dataGrid, columns, 0, ++r, Id.of(item).getValue());
                }
            } else if (exportMode == ExportMode.SELECTED_ROWS && dataGrid.getSelectedItems().size() > 0) {
                Set<Object> selected = dataGrid.getSelectedItems();
                List<Object> ordered = dataGridSource.getContainer().getItems().stream()
                        .filter(selected::contains)
                        .collect(Collectors.toList());

                for (Object item : ordered) {
                    if (checkIsRowNumberExceed(r)) {
                        break;
                    }

                    createDataGridRow(dataGrid, columns, 0, ++r, Id.of(item).getValue());
                }

            } else if (exportMode == ExportMode.CURRENT_PAGE) {
                if (dataGrid instanceof TreeDataGrid) {
                    TreeDataGrid treeDataGrid = (TreeDataGrid) dataGrid;
                    List<Object> items = dataGridSource.getContainer().getItems();

                    for (Object item : items) {
                        if (checkIsRowNumberExceed(r)) {
                            break;
                        }

                        r = createDataGridHierarchicalRow(treeDataGrid, ((ContainerTreeDataGridItems) dataGridSource),
                                columns, 0, r, item);
                    }
                } else {
                    for (Object itemId : dataGridSource.getContainer().getItems().stream()
                            .map(entity -> Id.of(entity).getValue())
                            .collect(Collectors.toList())
                    ) {
                        if (checkIsRowNumberExceed(r)) {
                            break;
                        }

                        createDataGridRow(dataGrid, columns, 0, ++r, itemId);
                    }
                }

            } else if (exportMode == ExportMode.ALL_ROWS) {
                boolean addLevelPadding = !(dataGrid instanceof TreeDataGrid);

                AllEntitiesLoader entitiesLoader = allEntitiesLoaderFactory.getEntitiesLoader();
                entitiesLoader.loadAll(
                        ((ListDataComponent<?>) dataGrid).getItems(),
                        context -> {
                            if (!checkIsRowNumberExceed(context.getEntityNumber())) {
                                createDataGridRowForEntityInstance(
                                        dataGrid,
                                        columns,
                                        0,
                                        context.getEntityNumber(),
                                        context.getEntity(),
                                        addLevelPadding);
                                return true;
                            }
                            return false;
                        });
            }

            for (int c = 0; c < columns.size(); c++) {
                sheet.setColumnWidth(c, sizers[c].getWidth() * COL_WIDTH_MAGIC);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                wb.write(out);
            } catch (IOException e) {
                throw new RuntimeException("Unable to write document", e);
            }

            if (isXlsxMaxRowNumberExceeded()) {
                showWarnNotification();
            }

            return new ByteArrayDownloadDataProvider(out.toByteArray(),
                    uiProperties.getSaveExportedByteArrayDataThresholdBytes(), coreProperties.getTempDir());

        } finally {
            disposeWorkBook();
        }
    }

    /**
     * CAUTION! Метод переопределён.
     * @param dataGrid
     * @param columns
     * @param startColumn
     * @param rowNumber
     * @param itemId
     */
    @SuppressWarnings("rawtypes")
    protected void createDataGridRow(Grid<?> dataGrid, List<DataGrid.Column<Object>> columns,
                                     int startColumn, int rowNumber, Object itemId) {
        Object entityInstance = null;
        if (customSelectionSupplier != null) {
            Map crossSelection = customSelectionSupplier.get();
            if (crossSelection.containsKey(itemId)) {
                entityInstance = crossSelection.get(itemId);
            }
        }
        if (entityInstance == null) {
            entityInstance = ((ContainerDataGridItems) ((ListDataComponent) dataGrid).getItems()).getItem(itemId);
        }
        createDataGridRowForEntityInstance(dataGrid, columns, startColumn, rowNumber, entityInstance, true);
    }

    /**
     * CAUTION! Скопирован из `io.jmix.gridexportflowui.exporter.excel.ExcelExporter#createStringCellValue(String)`.
     */
    private RichTextString createStringCellValue(String str) {
        return new XSSFRichTextString(str);
    }
}
