package gui;

import org.testng.annotations.Test;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.*;

/** Runs only with -Pdesktop-smoke and a display (CI uses Xvfb). */
public class DesktopSmoke {
    @Test(timeOut = 45000)
    public void opensPdfInBackgroundFiltersMetadataAndSwitchesTheme() throws Exception {
        AtomicReference<MetaViewerForm> window = new AtomicReference<>();
        try {
            MetaViewerForm.main(new String[]{"src/test/resources/Amazon-S3-Tutorial.pdf"});
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(30);
            boolean loaded = false;
            while (!loaded && System.nanoTime() < deadline) {
                AtomicReference<Boolean> ready = new AtomicReference<>(false);
                SwingUtilities.invokeAndWait(() -> {
                    for (Frame frame : Frame.getFrames()) {
                        if (frame instanceof MetaViewerForm form) {
                            window.set(form);
                            ready.set(form.getTitle().contains("Amazon-S3-Tutorial.pdf"));
                        }
                    }
                });
                loaded = ready.get();
                if (!loaded) {
                    Thread.sleep(50);
                }
            }
            assertThat(loaded).as("Background extraction must update the desktop window").isTrue();
            SwingUtilities.invokeAndWait(() -> {
                MetaViewerForm form = window.get();
                assertThat(form.isShowing()).isTrue();
                List<Component> components = descendants(form);
                JTable table = components.stream().filter(JTable.class::isInstance).map(JTable.class::cast).findFirst().orElseThrow();
                JTextField search = components.stream().filter(JTextField.class::isInstance).map(JTextField.class::cast).findFirst().orElseThrow();
                JTextArea preview = components.stream().filter(JTextArea.class::isInstance).map(JTextArea.class::cast).findFirst().orElseThrow();
                assertThat(preview.getText()).isNotBlank();
                int count = table.getRowCount();
                assertThat(count).isPositive();
                search.setText("no-field-matches-this-query-5921");
                assertThat(table.getRowCount()).isZero();
                search.setText("");
                assertThat(table.getRowCount()).isEqualTo(count);
                JButton theme = components.stream().filter(JButton.class::isInstance).map(JButton.class::cast)
                        .filter(button -> List.of("Light mode", "Dark mode").contains(button.getText())).findFirst().orElseThrow();
                String previous = theme.getText();
                theme.doClick();
                assertThat(theme.getText()).isNotEqualTo(previous);
            });
        } finally {
            SwingUtilities.invokeAndWait(() -> {
                for (Frame frame : Frame.getFrames()) {
                    if (frame instanceof MetaViewerForm) {
                        ((JFrame) frame).setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
                        frame.dispose();
                    }
                }
            });
        }
    }

    private static List<Component> descendants(Container parent) {
        List<Component> result = new ArrayList<>();
        for (Component component : parent.getComponents()) {
            result.add(component);
            if (component instanceof Container child) {
                result.addAll(descendants(child));
            }
        }
        return result;
    }
}
