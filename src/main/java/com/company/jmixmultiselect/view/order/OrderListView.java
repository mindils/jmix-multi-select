package com.company.jmixmultiselect.view.order;

import com.company.jmixmultiselect.action.AsyncExcelExportAction;
import com.company.jmixmultiselect.entity.Order;
import com.company.jmixmultiselect.view.main.MainView;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.data.selection.MultiSelectionEvent;
import com.vaadin.flow.data.selection.SelectionEvent;
import com.vaadin.flow.router.Route;
import io.jmix.core.DataManager;
import io.jmix.flowui.component.PaginationComponent;
import io.jmix.flowui.component.PaginationComponent.AfterRefreshEvent;
import io.jmix.flowui.component.grid.DataGrid;
import io.jmix.flowui.component.pagination.SimplePagination;
import io.jmix.flowui.model.CollectionChangeType;
import io.jmix.flowui.model.CollectionContainer;
import io.jmix.flowui.model.CollectionLoader;
import io.jmix.flowui.util.OperationResult;
import io.jmix.flowui.view.*;
import io.jmix.flowui.xml.layout.loader.PropertyParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;


@Route(value = "orders", layout = MainView.class)
@ViewController("bgxlsx_Order.list")
@ViewDescriptor("order-list-view.xml")
@LookupComponent("ordersDataGrid")
@DialogMode(width = "64em")
public class OrderListView extends StandardListView<Order> {
    private static final Logger log = LoggerFactory.getLogger(OrderListView.class);
    @Autowired
    private DataManager dataManager;

    @ViewComponent("ordersDataGrid.asyncExport")
    private AsyncExcelExportAction ordersDataGridAsyncExport;
    @ViewComponent
    private DataGrid<Order> ordersDataGrid;
    @ViewComponent
    private Span notificationBadge;
    @ViewComponent
    private CollectionContainer<Order> ordersDc;

    protected Map<Object, Order> crossPageSelection = new HashMap<>();

    private int generateSize = 10000;
    @ViewComponent
    private SimplePagination pagination;

    @Subscribe
    public void onInit(final InitEvent event) {
//        ordersDataGridAsyncExport.setCustomSelectionSupplier(() -> crossPageSelection);
//        ordersDataGrid.addSelectionListener(this::onDataGridSelection);
//        List<Order> orders = new ArrayList<>(generateSize);
//        for (int i = 0; i < generateSize; i++) {
//            Order order = dataManager.create(Order.class);
//            order.setNumber(RandomStringUtils.randomAlphabetic(10));
//            order.setDate(LocalDate.now());
//            order.setDescription(RandomStringUtils.randomAlphabetic(20));
//            order.setTotalCount(i);
//            orders.add(order);
//        }
//        dataManager.saveAll(orders);
    }

    private void onDataGridSelection(SelectionEvent<Grid<Order>, Order> event) {
        // Если данные загружаются, Grid сбрасывает выделение и бросается SelectionEvent.
        // Нужно пропустить это событие, поскольку потеряются сохранённые элементы
        if (!event.isFromClient()) {
            return;
        }

        if (event instanceof MultiSelectionEvent<Grid<Order>, Order> multiSelectionEvent) {
            multiSelectionEvent.getAddedSelection().forEach(item -> crossPageSelection.put(item.getId(), item));
            multiSelectionEvent.getRemovedSelection().forEach(item -> crossPageSelection.remove(item.getId()));

            updateNotificationBadge();
        }
    }

//    @Subscribe("pagination")
//    public void onPaginationAfterRefresh(final AfterRefreshEvent<SimplePagination> event) {
//        // Сбрасываем значение Checkbox из заголовка.
//        // При этом не успевает или не срабатывает `onDataGridSelection()` метод.
//        ordersDataGrid.deselectAll();
//        // Восстанавливаем выделение после переключения "страницы".
//        restoreSelection(crossPageSelection.values());
//    }

    private void updateNotificationBadge() {
        int size = crossPageSelection.size();
        if (size > 0) {
            notificationBadge.setText(String.valueOf(size));
            notificationBadge.setVisible(true);
        } else {
            notificationBadge.setVisible(false);
        }
    }

    private void restoreSelection(Collection<Order> selection) {
        ordersDataGrid.select(
                selection.stream()
                        .filter(ordersDc.getItems()::contains)
                        .toList());
    }
}