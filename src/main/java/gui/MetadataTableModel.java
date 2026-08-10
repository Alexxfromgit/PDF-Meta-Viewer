package gui;

import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class MetadataTableModel extends AbstractTableModel {
    private final List<Map.Entry<String, String>> rows = new ArrayList<>();

    void setMetadata(Map<String, String> metadata) {
        rows.clear();
        rows.addAll(metadata.entrySet());
        fireTableDataChanged();
    }

    Map.Entry<String, String> getEntry(int modelRow) {
        return rows.get(modelRow);
    }

    @Override
    public int getRowCount() {
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return 2;
    }

    @Override
    public String getColumnName(int column) {
        return column == 0 ? "Property" : "Value";
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
        Map.Entry<String, String> entry = rows.get(rowIndex);
        return columnIndex == 0 ? entry.getKey() : entry.getValue();
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        return String.class;
    }
}
