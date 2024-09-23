package com.company.jmixmultiselect.view.fragment.crossselection;

import com.company.jmixmultiselect.action.AsyncExcelExportAction;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.data.selection.MultiSelectionEvent;
import com.vaadin.flow.data.selection.SelectionEvent;
import io.jmix.core.entity.EntityValues;
import io.jmix.flowui.component.PaginationComponent.AfterRefreshEvent;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.pagination.SimplePagination;
import io.jmix.flowui.data.grid.ContainerDataGridItems;
import io.jmix.flowui.data.grid.DataGridItems;
import io.jmix.flowui.fragment.Fragment;
import io.jmix.flowui.fragment.FragmentDescriptor;
import io.jmix.flowui.kit.action.Action;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.view.ViewComponent;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@FragmentDescriptor("cross-selection-fragment.xml")
public class CrossSelectionFragment<T> extends Fragment<VerticalLayout> {

  private DataGrid<T> dataGrid;

  protected Map<Object, T> crossPageSelection = new HashMap<>();

  @ViewComponent
  private Span notificationBadge;

  public void setDataGrid(DataGrid<T> dataGrid) {
    this.dataGrid = dataGrid;

    AsyncExcelExportAction asyncExport = (AsyncExcelExportAction) dataGrid.getAction("asyncExport");
    if(asyncExport != null) {
      asyncExport.setCustomSelectionSupplier(() -> crossPageSelection);
    }

    dataGrid.addSelectionListener(this::onDataGridSelection);
  }

  public void setPagination(SimplePagination pagination) {
    pagination.addAfterRefreshListener(this::onPaginationAfterRefresh);
  }

  private void onPaginationAfterRefresh(AfterRefreshEvent<SimplePagination> simplePaginationAfterRefreshEvent) {
    dataGrid.deselectAll();
    restoreSelection(crossPageSelection.values());
  }

  private void onDataGridSelection(SelectionEvent<Grid<T>, T> event) {
    // Если данные загружаются, Grid сбрасывает выделение и бросается SelectionEvent.
    // Нужно пропустить это событие, поскольку потеряются сохранённые элементы
    if (!event.isFromClient()) {
      return;
    }

    if (event instanceof MultiSelectionEvent<Grid<T>, T> multiSelectionEvent) {
      multiSelectionEvent.getAddedSelection()
          .forEach(item -> crossPageSelection.put(EntityValues.getId(item), item));
      multiSelectionEvent.getRemovedSelection()
          .forEach(item -> crossPageSelection.remove(EntityValues.getId(item)));

      updateNotificationBadge();
    }
  }

  private void updateNotificationBadge() {
    int size = crossPageSelection.size();
    notificationBadge.setVisible(size > 0);
    if (size > 0) {
      notificationBadge.setText(String.valueOf(size));
    }
  }

  private void restoreSelection(Collection<T> selection) {
    DataGridItems<T> dataGridItems = dataGrid.getItems();
    if (dataGridItems instanceof ContainerDataGridItems<T> containerDataGridItems) {
      CollectionContainer<T> dc = containerDataGridItems.getContainer();

      Collection<T> itemsToSelect = selection.stream()
          .filter(dc.getItems()::contains)
          .collect(Collectors.toList());

      dataGrid.select(itemsToSelect);
    }
  }

}