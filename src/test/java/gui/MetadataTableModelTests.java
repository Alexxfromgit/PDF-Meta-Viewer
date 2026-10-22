package gui;

import org.testng.annotations.Test;

import javax.swing.JTable;
import javax.swing.RowFilter;
import javax.swing.SortOrder;
import javax.swing.SwingUtilities;
import javax.swing.table.TableRowSorter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.*;

public class MetadataTableModelTests {
    @Test
    public void presentsReadOnlyRowsAndNotifiesWhenMetadataIsReplaced() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MetadataTableModel model = new MetadataTableModel();
            AtomicInteger notifications = new AtomicInteger();
            model.addTableModelListener(event -> notifications.incrementAndGet());
            assertThat(model.getRowCount()).isZero();
            assertThat(model.getColumnCount()).isEqualTo(2);
            assertThat(model.getColumnName(0)).isEqualTo("Property");
            assertThat(model.getColumnName(1)).isEqualTo("Value");
            assertThat(model.getColumnClass(0)).isEqualTo(String.class);
            assertThat(model.getColumnClass(1)).isEqualTo(String.class);
            model.setMetadata(new TreeMap<>(Map.of("Author", "Ada", "Title", "Document")));
            assertThat(model.getRowCount()).isEqualTo(2);
            assertThat(model.getValueAt(0, 0)).isEqualTo("Author");
            assertThat(model.getValueAt(0, 1)).isEqualTo("Ada");
            assertThat(model.getEntry(1)).isEqualTo(Map.entry("Title", "Document"));
            assertThat(model.isCellEditable(0, 1)).isFalse();
            model.setMetadata(Map.of());
            assertThat(model.getRowCount()).isZero();
            assertThat(notifications.get()).isEqualTo(2);
        });
    }

    @Test
    public void filteringAndSortingPreserveCorrectCopySelection() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            MetadataTableModel model = new MetadataTableModel();
            model.setMetadata(new TreeMap<>(Map.of("Author", "Ada", "Title", "[PDF] Guide")));
            JTable table = new JTable(model);
            TableRowSorter<MetadataTableModel> sorter = new TableRowSorter<>(model);
            table.setRowSorter(sorter);
            sorter.setSortKeys(List.of(new javax.swing.RowSorter.SortKey(0, SortOrder.DESCENDING)));
            assertThat(model.getEntry(table.convertRowIndexToModel(0))).isEqualTo(Map.entry("Title", "[PDF] Guide"));
            sorter.setRowFilter(RowFilter.regexFilter("(?i)" + Pattern.quote("[pdf]")));
            assertThat(table.getRowCount()).isEqualTo(1);
            assertThat(table.getValueAt(0, 1)).isEqualTo("[PDF] Guide");
            sorter.setRowFilter(null);
            assertThat(table.getRowCount()).isEqualTo(2);
        });
    }
}
