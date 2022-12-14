package com.topsecret.model;


import com.topsecret.MainPanel;

import javax.swing.event.TableModelListener;
import javax.swing.table.TableModel;
import java.util.ArrayList;
import java.util.List;


/**
 *
 */
public class DataModel implements TableModel {

    private List<DataItem> lstItems = new ArrayList<>();
    private MainPanel parent = null;

    public DataModel(MainPanel parent) {
        this.parent = parent;
    }

    public void setParent(MainPanel parent) {
        this.parent = parent;
    }

    public List<DataItem> getLstItems() {
        return lstItems;
    }

    @Override
    public int getRowCount() {
        return lstItems.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int columnIndex) {
        switch (columnIndex) {
            case 0:
                return parent.getString("table.head.name");
            case 1:
                return parent.getString("table.head.size");
            default:
                break;
        }
        return null;
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        switch (columnIndex) {
            case 0:
                return String.class;
            case 1:
                return String.class;
            default:
                break;
        }
        return null;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        DataItem item = lstItems.get(rowIndex);
        return ((columnIndex == 0) && (!item.isEncrypted()));
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        DataItem item = lstItems.get(rowIndex);
        switch (columnIndex) {
            case 0:
                return (item.isEncrypted() ? parent.getString("table.col.name.encrypted") : item.getName());
            case 1:
                int len = item.getLength();
                return parent.getString("table.col.size", len);
            default:
                break;
        }
        return null;
    }

    @Override
    public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
        if (columnIndex == 0) {
            DataItem item = lstItems.get(rowIndex);
            item.setName((String) aValue);
            parent.refreshView();
        }
    }

    @Override
    public void addTableModelListener(TableModelListener l) {

    }

    @Override
    public void removeTableModelListener(TableModelListener l) {

    }
}